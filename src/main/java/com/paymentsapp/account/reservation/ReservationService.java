package com.paymentsapp.account.reservation;

import static com.paymentsapp.account.error.AccountError.*;

import com.paymentsapp.account.limits.LimitsService;
import com.paymentsapp.account.limits.SourceKind;
import com.paymentsapp.account.limits.UsageCounters;
import com.paymentsapp.account.reservation.check.ReservationCheck;
import com.paymentsapp.account.reservation.check.ReservationContext;
import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountRepository;
import com.paymentsapp.account.user.User;
import com.paymentsapp.account.user.UserRepository;
import com.paymentsapp.platform.error.DomainException;
import com.paymentsapp.platform.outbox.Outbox;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Резервирование средств под платёж. Все операции идемпотентны по paymentId:
// payment может повторить вызов после тайм-аута и получит тот же результат.
// Правила резерва — отдельные проверки ReservationCheck (по порядку @Order).
@Service
public class ReservationService {

    private static final String EMPTY = "";

    private final UserRepository users;
    private final AccountRepository accounts;
    private final ReservationRepository reservations;
    private final List<ReservationCheck> checks;
    private final LimitsService limits;
    private final UsageCounters usage;
    private final Outbox outbox;
    private final Clock clock;
    private final String topic;

    public ReservationService(UserRepository users, AccountRepository accounts, ReservationRepository reservations,
                              List<ReservationCheck> checks, LimitsService limits, UsageCounters usage, Outbox outbox,
                              Clock clock, @Value("${messaging.account-reservations.topic}") String topic) {
        this.users = users;
        this.accounts = accounts;
        this.reservations = reservations;
        this.checks = checks;
        this.limits = limits;
        this.usage = usage;
        this.outbox = outbox;
        this.clock = clock;
        this.topic = topic;
    }

    @Transactional
    public Reservation reserve(ReserveCommand cmd) {
        // Сначала блокируем пользователя: дальше все проверки и счётчики лимитов — без гонок
        User user = users.findByIdForUpdate(cmd.userId()).orElseThrow(() -> new DomainException(USER_NOT_FOUND));

        var existing = reservations.findByPaymentId(cmd.paymentId());
        if (existing.isPresent()) {
            Reservation r = existing.get();
            if (!r.sameRequest(cmd.userId(), cmd.accountId(), cmd.recipientAccountId(), cmd.amount(), cmd.commission())) {
                throw new DomainException(RESERVATION_CONFLICT);
            }
            return r;
        }

        Account source = accounts.findByIdForUpdate(cmd.accountId()).orElseThrow(() -> new DomainException(ACCOUNT_NOT_FOUND));
        Account recipient = cmd.recipientAccountId() == null ? null : accounts.findById(cmd.recipientAccountId()).orElse(null);
        OffsetDateTime now = OffsetDateTime.now(clock);
        ReservationContext context = new ReservationContext(cmd, user, source, recipient,
                limits.of(source, user.getIdentificationLevel()), now);
        checks.forEach(check -> check.check(context));

        Reservation reservation = new Reservation(cmd.paymentId(), cmd.userId(), cmd.accountId(), cmd.recipientAccountId(),
                cmd.operationType(), cmd.amount(), cmd.commission(), now);
        source.reserve(reservation.getTotal());
        reservations.save(reservation);
        usage.add(usageOf(reservation, source));
        publish(ReservationEvent.FUNDS_RESERVED, reservation);
        return reservation;
    }

    @Transactional
    public Reservation release(UUID paymentId) {
        Reservation r = lockedReservation(paymentId);
        switch (r.getStatus()) {
            case RELEASED -> {
                return r;
            }
            case CAPTURED -> throw new DomainException(RESERVATION_CAPTURED);
            case RESERVED -> {
                Account account = accounts.findByIdForUpdate(r.getAccountId()).orElseThrow();
                account.release(r.getTotal());
                // Снятый резерв не расходует лимит
                usage.subtract(usageOf(r, account));
                r.changeStatus(ReservationStatus.RELEASED, OffsetDateTime.now(clock));
                publish(ReservationEvent.FUNDS_RELEASED, r);
                return r;
            }
        }
        throw new IllegalStateException();
    }

    @Transactional
    public Reservation capture(UUID paymentId) {
        Reservation r = lockedReservation(paymentId);
        switch (r.getStatus()) {
            case CAPTURED -> {
                return r;
            }
            case RELEASED -> throw new DomainException(RESERVATION_RELEASED);
            case RESERVED -> {
                // Счета блокируем в порядке id: встречные переводы A→B и B→A не взаимоблокируются
                Map<UUID, Account> locked = lockInOrder(r.getAccountId(), r.getRecipientAccountId());
                locked.get(r.getAccountId()).capture(r.getTotal());
                if (r.getRecipientAccountId() != null) {
                    // Получатель — наш клиент: зачисляем сумму перевода без комиссии
                    locked.get(r.getRecipientAccountId()).credit(r.getAmount());
                }
                r.changeStatus(ReservationStatus.CAPTURED, OffsetDateTime.now(clock));
                publish(ReservationEvent.FUNDS_CAPTURED, r);
                return r;
            }
        }
        throw new IllegalStateException();
    }

    private Reservation lockedReservation(UUID paymentId) {
        Reservation r = reservations.findByPaymentId(paymentId).orElseThrow(() -> new DomainException(RESERVATION_NOT_FOUND));
        // Та же блокировка, что и при резервировании, — release и capture не пересекутся
        users.findByIdForUpdate(r.getUserId()).orElseThrow();
        return r;
    }

    private Map<UUID, Account> lockInOrder(UUID... ids) {
        Map<UUID, Account> locked = new HashMap<>();
        Stream.of(ids).filter(Objects::nonNull).distinct().sorted(Comparator.naturalOrder())
                .forEach(id -> locked.put(id, accounts.findByIdForUpdate(id).orElseThrow()));
        return locked;
    }

    private static UsageCounters.Usage usageOf(Reservation r, Account source) {
        return new UsageCounters.Usage(r.getUserId(), r.getOperationType(), SourceKind.of(source.getType()),
                r.getAccountId(), r.getAmount(), r.getTotal(), r.getCreatedAt());
    }

    private void publish(ReservationEvent event, Reservation r) {
        outbox.add(topic, r.getPaymentId().toString(), event.type(), Map.of(
                "reservationId", r.getId(),
                "paymentId", r.getPaymentId(),
                "userId", r.getUserId(),
                "accountId", r.getAccountId(),
                "recipientAccountId", Objects.toString(r.getRecipientAccountId(), EMPTY),
                "operationType", r.getOperationType(),
                "amount", r.getAmount(),
                "commission", r.getCommission(),
                "total", r.getTotal(),
                "status", r.getStatus()));
    }
}
