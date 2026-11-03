package com.paymentsapp.account.reservation;

import static com.paymentsapp.account.reservation.ReservationException.Code.*;

import com.paymentsapp.account.outbox.Outbox;
import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountRepository;
import com.paymentsapp.account.user.AccountStatus;
import com.paymentsapp.account.user.UserRepository;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Резервирование средств под платёж. Все операции идемпотентны по paymentId:
// payment может повторить вызов после таймаута и получит тот же результат.
@Service
public class ReservationService {

    public static final String TOPIC = "account.reservations";
    // Дневной и месячный лимиты считаем по московскому времени
    private static final ZoneId LIMITS_ZONE = ZoneId.of("Europe/Moscow");

    private final UserRepository users;
    private final AccountRepository accounts;
    private final ReservationRepository reservations;
    private final LimitRuleRepository limits;
    private final Outbox outbox;
    private final Clock clock;

    @Autowired
    public ReservationService(UserRepository users, AccountRepository accounts, ReservationRepository reservations,
                              LimitRuleRepository limits, Outbox outbox) {
        this(users, accounts, reservations, limits, outbox, Clock.system(LIMITS_ZONE));
    }

    ReservationService(UserRepository users, AccountRepository accounts, ReservationRepository reservations,
                       LimitRuleRepository limits, Outbox outbox, Clock clock) {
        this.users = users;
        this.accounts = accounts;
        this.reservations = reservations;
        this.limits = limits;
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
            if (!r.sameRequest(cmd.userId(), cmd.accountId(), cmd.amount(), cmd.commission())) {
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

        checkLimits(cmd);

        Reservation reservation = new Reservation(cmd.paymentId(), cmd.userId(), cmd.accountId(),
                cmd.operationType(), cmd.amount(), cmd.commission());
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
                accounts.findByIdForUpdate(r.getAccountId()).orElseThrow().capture(r.getTotal());
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
                "operationType", r.getOperationType(),
                "amount", r.getAmount(),
                "commission", r.getCommission(),
                "total", r.getTotal(),
                "status", r.getStatus()));
    }
}
