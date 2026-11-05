package com.paymentsapp.account.reservation;

import static com.paymentsapp.account.reservation.ReservationException.Code.*;

import com.paymentsapp.platform.outbox.Outbox;
import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountRepository;
import com.paymentsapp.account.user.AccountStatus;
import com.paymentsapp.account.user.AccountType;
import com.paymentsapp.account.user.User;
import com.paymentsapp.account.user.UserRepository;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Резервирование средств под платёж. Все операции идемпотентны по paymentId:
// payment может повторить вызов после таймаута и получит тот же результат.
@Service
public class ReservationService {

    static final String OWN_TRANSFER = "OWN_TRANSFER";
    static final String WALLET_TRANSFER = "WALLET_TRANSFER";
    static final String ABROAD = "ABROAD";
    // Переводы людям и по реквизитам — от 10 ₽ (документ «Переводы за рубеж», раздел о СБП, карте и реквизитах)
    private static final java.util.Set<String> MIN_10 = java.util.Set.of("SBP_PHONE", "CARD2CARD", "REQUISITES", "REQUISITES_OUR");
    private static final BigDecimal MIN_TRANSFER = BigDecimal.TEN;
    // За рубеж без открытия счёта (с кошелька) и со счёта (с карты): на операцию и в месяц
    private static final BigDecimal ABROAD_WALLET_SINGLE = BigDecimal.valueOf(300_000);
    private static final BigDecimal ABROAD_WALLET_MONTH = BigDecimal.valueOf(400_000);
    private static final BigDecimal ABROAD_CARD_SINGLE = BigDecimal.valueOf(400_000);
    private static final BigDecimal ABROAD_CARD_MONTH = BigDecimal.valueOf(3_000_000);

    private final UserRepository users;
    private final AccountRepository accounts;
    private final ReservationRepository reservations;
    private final LimitRuleRepository limits;
    private final AccountLimitsRepository accountLimits;
    private final Outbox outbox;
    // Дневной и месячный лимиты считаем по часовому поясу бизнеса (platform.time.zone)
    private final Clock clock;
    private final String topic;

    public ReservationService(UserRepository users, AccountRepository accounts, ReservationRepository reservations,
                              LimitRuleRepository limits, AccountLimitsRepository accountLimits, Outbox outbox, Clock clock,
                              @Value("${messaging.account-reservations.topic}") String topic) {
        this.topic = topic;
        this.users = users;
        this.accounts = accounts;
        this.reservations = reservations;
        this.limits = limits;
        this.accountLimits = accountLimits;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public Reservation reserve(ReserveCommand cmd) {
        // Сначала блокируем пользователя: дальше все проверки идут без гонок
        User user = users.findByIdForUpdate(cmd.userId())
                .orElseThrow(() -> new ReservationException(ACCOUNT_NOT_FOUND, "Пользователь не найден"));

        var existing = reservations.findByPaymentId(cmd.paymentId());
        if (existing.isPresent()) {
            Reservation r = existing.get();
            if (!r.sameRequest(cmd.userId(), cmd.accountId(), cmd.recipientAccountId(), cmd.amount(), cmd.commission())) {
                throw new ReservationException(RESERVATION_CONFLICT, "Платёж уже зарезервирован с другими параметрами");
            }
            return r;
        }

        Account account = accounts.findByIdForUpdate(cmd.accountId())
                .orElseThrow(() -> new ReservationException(ACCOUNT_NOT_FOUND, "Счёт не найден"));
        if (!account.getUserId().equals(cmd.userId())) {
            throw new ReservationException(ACCOUNT_NOT_OWNED, "Счёт принадлежит другому пользователю");
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ReservationException(ACCOUNT_BLOCKED,
                    account.getStatus() == AccountStatus.CLOSED ? "Карта отвязана" : "Счёт заблокирован");
        }
        if (!account.getCurrency().equals(cmd.currency())) {
            throw new ReservationException(CURRENCY_MISMATCH, "Валюта счёта не совпадает с валютой платежа");
        }

        // Без идентификации нельзя переводить людям, без полной — за рубеж
        String unavailable = user.getIdentificationLevel().unavailableReason(cmd.operationType(), cmd.providerGroup(),
                account.getType() == AccountType.WALLET);
        if (unavailable != null) {
            throw new ReservationException(IDENTIFICATION_REQUIRED, unavailable);
        }

        if (cmd.recipientAccountId() != null) {
            checkRecipient(account, cmd.recipientAccountId(), cmd.amount(), cmd.operationType());
        }
        checkMinAmount(cmd);
        checkLimits(cmd);
        if (ABROAD.equals(cmd.operationType())) {
            checkAbroadLimits(account, cmd);
        }
        checkAccountLimits(account, cmd.amount().add(cmd.commission()));

        Reservation reservation = new Reservation(cmd.paymentId(), cmd.userId(), cmd.accountId(),
                cmd.recipientAccountId(), cmd.operationType(), cmd.amount(), cmd.commission());
        // С карты другого банка деньги списывает процессинг — её баланс мы не проверяем
        if (!account.isExternal() && account.available().compareTo(reservation.getTotal()) < 0) {
            throw new ReservationException(INSUFFICIENT_FUNDS, "Недостаточно средств");
        }
        account.reserve(reservation.getTotal());
        reservations.save(reservation);
        publish("FundsReserved", reservation);
        return reservation;
    }

    @Transactional
    public Reservation release(UUID paymentId) {
        Reservation r = lockedReservation(paymentId);
        switch (r.getStatus()) {
            case RELEASED -> { return r; }
            case CAPTURED -> throw new ReservationException(RESERVATION_CONFLICT, "Резерв уже списан, снять нельзя");
            case RESERVED -> {
                accounts.findByIdForUpdate(r.getAccountId()).orElseThrow().release(r.getTotal());
                r.changeStatus(ReservationStatus.RELEASED);
                publish("FundsReleased", r);
                return r;
            }
        }
        throw new IllegalStateException();
    }

    @Transactional
    public Reservation capture(UUID paymentId) {
        Reservation r = lockedReservation(paymentId);
        switch (r.getStatus()) {
            case CAPTURED -> { return r; }
            case RELEASED -> throw new ReservationException(RESERVATION_CONFLICT, "Резерв уже снят, списать нельзя");
            case RESERVED -> {
                // Счета блокируем в порядке id: встречные переводы A→B и B→A не взаимоблокируются
                Map<UUID, Account> locked = lockInOrder(r.getAccountId(), r.getRecipientAccountId());
                locked.get(r.getAccountId()).capture(r.getTotal());
                if (r.getRecipientAccountId() != null) {
                    // Получатель — наш клиент: зачисляем сумму перевода без комиссии
                    locked.get(r.getRecipientAccountId()).credit(r.getAmount());
                }
                r.changeStatus(ReservationStatus.CAPTURED);
                publish("FundsCaptured", r);
                return r;
            }
        }
        throw new IllegalStateException();
    }

    private Reservation lockedReservation(UUID paymentId) {
        Reservation r = reservations.findByPaymentId(paymentId)
                .orElseThrow(() -> new ReservationException(RESERVATION_NOT_FOUND, "Резервирование не найдено"));
        // Та же блокировка, что и при резервировании, — release и capture не пересекутся
        users.findByIdForUpdate(r.getUserId()).orElseThrow();
        return r;
    }

    // Принимать переводы может только активная карта в той же валюте; кошелёк — нет
    // Чужие переводы принимает только карта; между своими счетами можно и на «Мой кошелёк», но только себе
    // Чужим переводам — только на карту; «на кошелёк» — только на чужой «Мой кошелёк»; между своими — на любой свой счёт
    private void checkRecipient(Account source, UUID recipientAccountId, BigDecimal amount, String operationType) {
        boolean ownTransfer = OWN_TRANSFER.equals(operationType);
        boolean walletTransfer = WALLET_TRANSFER.equals(operationType);
        Account recipient = accounts.findById(recipientAccountId)
                .orElseThrow(() -> new ReservationException(RECIPIENT_INVALID, "Счёт получателя не найден"));
        if (ownTransfer && !recipient.getUserId().equals(source.getUserId())) {
            throw new ReservationException(RECIPIENT_INVALID, "Счёт зачисления принадлежит другому пользователю");
        }
        // На свою привязанную карту другого банка перевести можно (доставит процессинг), на чужую — нет
        if (recipient.isExternal() && !ownTransfer) {
            throw new ReservationException(RECIPIENT_INVALID, "Зачислить на карту другого банка отсюда нельзя");
        }
        // Электронные деньги нельзя получить в кредит: с кредитной карты кошелёк не пополняем
        if (recipient.getType() == AccountType.WALLET && source.isCredit()) {
            throw new ReservationException(RECIPIENT_INVALID, "Пополнить кошелёк с кредитной карты нельзя");
        }
        if (walletTransfer && !recipient.getUserId().equals(source.getUserId())
                && users.findById(recipient.getUserId()).map(u -> !u.getIdentificationLevel().receivesFromOthers()).orElse(false)) {
            throw new ReservationException(RECIPIENT_INVALID,
                    "Кошелёк получателя не принимает переводы: получатель не прошёл идентификацию");
        }
        if (walletTransfer && recipient.getType() != AccountType.WALLET) {
            throw new ReservationException(RECIPIENT_INVALID, "Перевод на кошелёк зачисляется только на «Мой кошелёк»");
        }
        if (!ownTransfer && !walletTransfer && recipient.getType() != AccountType.CARD) {
            throw new ReservationException(RECIPIENT_INVALID, "Переводить можно только на карту");
        }
        if (recipient.getStatus() != AccountStatus.ACTIVE) {
            throw new ReservationException(RECIPIENT_INVALID, "Счёт получателя заблокирован");
        }
        if (recipient.getId().equals(source.getId())) {
            throw new ReservationException(RECIPIENT_INVALID, "Нельзя перевести на тот же счёт");
        }
        if (!recipient.getCurrency().equals(source.getCurrency())) {
            throw new ReservationException(CURRENCY_MISMATCH, "Валюта карты получателя другая");
        }
        // Максимальный остаток проверяем при резерве: после списания отказать в зачислении уже нельзя
        AccountLimits recipientLimits = limitsOf(recipient);
        BigDecimal maxBalance = recipientLimits == null ? null : recipientLimits.getMaxBalance();
        if (maxBalance != null && recipient.getBalance().add(amount).compareTo(maxBalance) > 0) {
            throw new ReservationException(RECIPIENT_LIMIT_EXCEEDED,
                    "Превышен максимальный остаток на счёте получателя: " + rub(maxBalance));
        }
    }

    // Лимиты счёта списания: сумма платежа и оборот за календарный месяц (вместе с комиссией)
    private void checkAccountLimits(Account account, BigDecimal total) {
        AccountLimits l = limitsOf(account);
        if (l == null) {
            return;
        }
        if (l.getMaxPayment() != null && total.compareTo(l.getMaxPayment()) > 0) {
            throw new ReservationException(LIMIT_EXCEEDED, "Превышена максимальная сумма платежа: " + rub(l.getMaxPayment()));
        }
        if (l.getMaxMonthlyTurnover() != null) {
            BigDecimal used = reservations.accountTurnoverSince(account.getId(),
                    startOf(LocalDate.now(clock).withDayOfMonth(1)));
            if (used.add(total).compareTo(l.getMaxMonthlyTurnover()) > 0) {
                throw new ReservationException(LIMIT_EXCEEDED, "Превышена максимальная сумма операций в месяц: "
                        + rub(l.getMaxMonthlyTurnover()) + ", доступно ещё "
                        + rub(l.getMaxMonthlyTurnover().subtract(used).max(BigDecimal.ZERO)));
            }
        }
    }

    // Лимиты «Моего кошелька» — по уровню идентификации владельца, остальных счетов — из account_limits
    private AccountLimits limitsOf(Account account) {
        if (account.getType() == AccountType.WALLET) {
            return users.findById(account.getUserId())
                    .map(u -> AccountLimits.forWallet(account.getId(), u.getIdentificationLevel())).orElse(null);
        }
        return accountLimits.findById(account.getId()).orElse(null);
    }

    private Map<UUID, Account> lockInOrder(UUID... ids) {
        Map<UUID, Account> locked = new java.util.HashMap<>();
        Stream.of(ids).filter(Objects::nonNull).distinct().sorted(Comparator.naturalOrder())
                .forEach(id -> locked.put(id, accounts.findByIdForUpdate(id).orElseThrow()));
        return locked;
    }

    private void checkMinAmount(ReserveCommand cmd) {
        if (MIN_10.contains(cmd.operationType()) && cmd.amount().compareTo(MIN_TRANSFER) < 0) {
            throw new ReservationException(LIMIT_EXCEEDED, "Минимальная сумма перевода — " + rub(MIN_TRANSFER));
        }
    }

    // За рубеж: с кошелька (без открытия счёта) — 300 000 ₽ за раз и 400 000 ₽ в месяц, с карты (со счёта) —
    // 400 000 ₽ и 3 000 000 ₽. Лимиты кошелька по уровню идентификации проверяются отдельно
    private void checkAbroadLimits(Account account, ReserveCommand cmd) {
        boolean wallet = account.getType() == AccountType.WALLET;
        BigDecimal single = wallet ? ABROAD_WALLET_SINGLE : ABROAD_CARD_SINGLE;
        BigDecimal month = wallet ? ABROAD_WALLET_MONTH : ABROAD_CARD_MONTH;
        if (cmd.amount().compareTo(single) > 0) {
            throw new ReservationException(LIMIT_EXCEEDED, "Превышен лимит перевода за рубеж на одну операцию: " + rub(single));
        }
        OffsetDateTime from = startOf(LocalDate.now(clock).withDayOfMonth(1));
        BigDecimal walletUsed = accounts.findByUserIdOrderByTypeDescCreatedAt(cmd.userId()).stream()
                .filter(a -> a.getType() == AccountType.WALLET)
                .map(a -> reservations.accountUsedSince(a.getId(), ABROAD, from))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal used = wallet ? walletUsed : reservations.usedSince(cmd.userId(), ABROAD, from).subtract(walletUsed);
        if (used.add(cmd.amount()).compareTo(month) > 0) {
            throw new ReservationException(LIMIT_EXCEEDED, "Превышен месячный лимит переводов за рубеж"
                    + (wallet ? " с кошелька: " : " с карт: ") + rub(month) + ", доступно ещё "
                    + rub(month.subtract(used).max(BigDecimal.ZERO)));
        }
    }

    private void checkLimits(ReserveCommand cmd) {
        LocalDate today = LocalDate.now(clock);
        for (LimitRule rule : limits.findByUserIdAndOperationType(cmd.userId(), cmd.operationType())) {
            BigDecimal used = switch (rule.getPeriod()) {
                case SINGLE -> BigDecimal.ZERO;
                case DAY -> reservations.usedSince(cmd.userId(), cmd.operationType(), startOf(today));
                case MONTH -> reservations.usedSince(cmd.userId(), cmd.operationType(), startOf(today.withDayOfMonth(1)));
            };
            if (used.add(cmd.amount()).compareTo(rule.getMaxAmount()) > 0) {
                throw new ReservationException(LIMIT_EXCEEDED, switch (rule.getPeriod()) {
                    case SINGLE -> "Превышен лимит на одну операцию: " + rub(rule.getMaxAmount());
                    case DAY -> "Превышен дневной лимит: " + rub(rule.getMaxAmount())
                            + ", доступно ещё " + rub(rule.getMaxAmount().subtract(used).max(BigDecimal.ZERO));
                    case MONTH -> "Превышен месячный лимит: " + rub(rule.getMaxAmount())
                            + ", доступно ещё " + rub(rule.getMaxAmount().subtract(used).max(BigDecimal.ZERO));
                });
            }
        }
    }

    private static String rub(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru"));
        // 50 000 ₽, но 33 739,30 ₽
        boolean whole = value.stripTrailingZeros().scale() <= 0;
        format.setMinimumFractionDigits(whole ? 0 : 2);
        format.setMaximumFractionDigits(2);
        return format.format(value) + " ₽";
    }

    private OffsetDateTime startOf(LocalDate day) {
        return day.atStartOfDay(clock.getZone()).toOffsetDateTime();
    }

    private void publish(String eventType, Reservation r) {
        outbox.add(topic, r.getPaymentId().toString(), eventType, Map.of(
                "reservationId", r.getId(),
                "paymentId", r.getPaymentId(),
                "userId", r.getUserId(),
                "accountId", r.getAccountId(),
                "recipientAccountId", Objects.toString(r.getRecipientAccountId(), ""),
                "operationType", r.getOperationType(),
                "amount", r.getAmount(),
                "commission", r.getCommission(),
                "total", r.getTotal(),
                "status", r.getStatus()));
    }
}
