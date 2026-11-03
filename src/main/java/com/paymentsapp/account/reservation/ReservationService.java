package com.paymentsapp.account.reservation;

import static com.paymentsapp.account.reservation.ReservationException.Code.*;

import com.paymentsapp.account.outbox.Outbox;
import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountRepository;
import com.paymentsapp.account.user.AccountStatus;
import com.paymentsapp.account.user.AccountType;
import com.paymentsapp.account.user.UserRepository;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Резервирование средств под платёж. Все операции идемпотентны по paymentId:
// payment может повторить вызов после таймаута и получит тот же результат.
@Service
public class ReservationService {

    public static final String TOPIC = "account.reservations";
    static final String OWN_TRANSFER = "OWN_TRANSFER";
    // Дневной и месячный лимиты считаем по московскому времени
    private static final ZoneId LIMITS_ZONE = ZoneId.of("Europe/Moscow");

    private final UserRepository users;
    private final AccountRepository accounts;
    private final ReservationRepository reservations;
    private final LimitRuleRepository limits;
    private final AccountLimitsRepository accountLimits;
    private final Outbox outbox;
    private final Clock clock;

    @Autowired
    public ReservationService(UserRepository users, AccountRepository accounts, ReservationRepository reservations,
                              LimitRuleRepository limits, AccountLimitsRepository accountLimits, Outbox outbox) {
        this(users, accounts, reservations, limits, accountLimits, outbox, Clock.system(LIMITS_ZONE));
    }

    ReservationService(UserRepository users, AccountRepository accounts, ReservationRepository reservations,
                       LimitRuleRepository limits, AccountLimitsRepository accountLimits, Outbox outbox, Clock clock) {
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
        users.findByIdForUpdate(cmd.userId())
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
            throw new ReservationException(ACCOUNT_BLOCKED, "Счёт заблокирован");
        }
        if (!account.getCurrency().equals(cmd.currency())) {
            throw new ReservationException(CURRENCY_MISMATCH, "Валюта счёта не совпадает с валютой платежа");
        }

        if (cmd.recipientAccountId() != null) {
            checkRecipient(account, cmd.recipientAccountId(), cmd.amount(), OWN_TRANSFER.equals(cmd.operationType()));
        }
        checkLimits(cmd);
        checkAccountLimits(account, cmd.amount().add(cmd.commission()));

        Reservation reservation = new Reservation(cmd.paymentId(), cmd.userId(), cmd.accountId(),
                cmd.recipientAccountId(), cmd.operationType(), cmd.amount(), cmd.commission());
        if (account.available().compareTo(reservation.getTotal()) < 0) {
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
    private void checkRecipient(Account source, UUID recipientAccountId, BigDecimal amount, boolean ownTransfer) {
        Account recipient = accounts.findById(recipientAccountId)
                .orElseThrow(() -> new ReservationException(RECIPIENT_INVALID, "Счёт получателя не найден"));
        if (ownTransfer && !recipient.getUserId().equals(source.getUserId())) {
            throw new ReservationException(RECIPIENT_INVALID, "Счёт зачисления принадлежит другому пользователю");
        }
        if (!ownTransfer && recipient.getType() != AccountType.CARD) {
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
        BigDecimal maxBalance = accountLimits.findById(recipientAccountId).map(AccountLimits::getMaxBalance).orElse(null);
        if (maxBalance != null && recipient.getBalance().add(amount).compareTo(maxBalance) > 0) {
            throw new ReservationException(RECIPIENT_LIMIT_EXCEEDED,
                    "Превышен максимальный остаток на счёте получателя: " + rub(maxBalance));
        }
    }

    // Лимиты счёта списания: сумма платежа и оборот за календарный месяц (вместе с комиссией)
    private void checkAccountLimits(Account account, BigDecimal total) {
        AccountLimits l = accountLimits.findById(account.getId()).orElse(null);
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

    private Map<UUID, Account> lockInOrder(UUID... ids) {
        Map<UUID, Account> locked = new java.util.HashMap<>();
        Stream.of(ids).filter(Objects::nonNull).distinct().sorted(Comparator.naturalOrder())
                .forEach(id -> locked.put(id, accounts.findByIdForUpdate(id).orElseThrow()));
        return locked;
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
        return day.atStartOfDay(LIMITS_ZONE).toOffsetDateTime();
    }

    private void publish(String eventType, Reservation r) {
        outbox.add(TOPIC, r.getPaymentId().toString(), eventType, Map.of(
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
