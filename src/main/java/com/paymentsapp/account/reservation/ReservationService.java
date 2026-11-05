package com.paymentsapp.account.reservation;

import static com.paymentsapp.account.error.AccountError.*;

import com.paymentsapp.account.journal.Journal;
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
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Резервирование средств под платёж. Все операции идемпотентны по paymentId:
// payment может повторить вызов после тайм-аута и получит тот же результат.
// Правила резерва — отдельные проверки ReservationCheck (по порядку @Order).
// Каждое движение денег (списание, зачисление) пишется в журнал account_operations в той же транзакции.
@Service
public class ReservationService {

    private final UserRepository users;
    private final AccountRepository accounts;
    private final ReservationRepository reservations;
    private final List<ReservationCheck> checks;
    private final LimitsService limits;
    private final UsageCounters usage;
    private final Journal journal;
    private final Clock clock;

    public ReservationService(UserRepository users, AccountRepository accounts, ReservationRepository reservations,
                              List<ReservationCheck> checks, LimitsService limits, UsageCounters usage, Journal journal,
                              Clock clock) {
        this.users = users;
        this.accounts = accounts;
        this.reservations = reservations;
        this.checks = checks;
        this.limits = limits;
        this.usage = usage;
        this.journal = journal;
        this.clock = clock;
    }

    @Transactional
    public Reservation reserve(ReserveCommand cmd) {
        // Сначала блокируем пользователя: дальше все проверки и счётчики лимитов — без гонок
        User user = lockUser(cmd.userId());
        Optional<Reservation> existing = sameReservation(cmd);
        if (existing.isPresent()) {
            return existing.get();
        }
        Account source = accounts.findByIdForUpdate(cmd.accountId()).orElseThrow(() -> new DomainException(ACCOUNT_NOT_FOUND));
        Account recipient = cmd.recipientAccountId() == null ? null : accounts.findById(cmd.recipientAccountId()).orElse(null);
        return reserve(cmd, user, source, recipient, OffsetDateTime.now(clock));
    }

    // Перевод внутри банка (между своими счетами, в кошелёк другого клиента) одной транзакцией: те же проверки,
    // что у резерва (лимиты, максимальный остаток получателя, запреты уровня идентификации), сразу списание
    // и зачисление. Нет промежуточного состояния «деньги зарезервированы, а account упал до подтверждения».
    @Transactional
    public Reservation transfer(ReserveCommand cmd) {
        User user = lockUser(cmd.userId());
        Optional<Reservation> existing = sameReservation(cmd);
        if (existing.isPresent()) {
            // Повтор после тайм-аута: перевод уже проведён (или проведём недоделанный)
            return existing.get().getStatus() == ReservationStatus.RESERVED ? capture(cmd.paymentId()) : existing.get();
        }
        // Оба счёта — сразу и в порядке id: встречные переводы A→B и B→A не взаимоблокируются
        Map<UUID, Account> locked = lockInOrder(cmd.accountId(), cmd.recipientAccountId());
        Account source = Optional.ofNullable(locked.get(cmd.accountId())).orElseThrow(() -> new DomainException(ACCOUNT_NOT_FOUND));
        Account recipient = cmd.recipientAccountId() == null ? null : locked.get(cmd.recipientAccountId());
        OffsetDateTime now = OffsetDateTime.now(clock);
        Reservation reservation = reserve(cmd, user, source, recipient, now);
        move(reservation, source, recipient, now);
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
                move(r, locked.get(r.getAccountId()), r.getRecipientAccountId() == null ? null
                        : locked.get(r.getRecipientAccountId()), OffsetDateTime.now(clock));
                return r;
            }
        }
        throw new IllegalStateException();
    }

    private Reservation reserve(ReserveCommand cmd, User user, Account source, Account recipient, OffsetDateTime now) {
        ReservationContext context = new ReservationContext(cmd, user, source, recipient,
                limits.of(source, user.getIdentificationLevel()), now);
        checks.forEach(check -> check.check(context));

        Reservation reservation = new Reservation(cmd.paymentId(), cmd.userId(), cmd.accountId(), cmd.recipientAccountId(),
                cmd.operationType(), cmd.amount(), cmd.commission(), now);
        source.reserve(reservation.getTotal());
        reservations.save(reservation);
        usage.add(usageOf(reservation, source));
        return reservation;
    }

    // Резерв → списание; получатель — наш клиент: зачисляем сумму перевода без комиссии
    private void move(Reservation r, Account source, Account recipient, OffsetDateTime now) {
        source.capture(r.getTotal());
        journal.debit(source, r.getPaymentId(), r.getTotal(), now);
        if (recipient != null) {
            recipient.credit(r.getAmount());
            journal.credit(recipient, r.getPaymentId(), r.getAmount(), now);
        }
        r.changeStatus(ReservationStatus.CAPTURED, now);
    }

    private User lockUser(UUID userId) {
        return users.findByIdForUpdate(userId).orElseThrow(() -> new DomainException(USER_NOT_FOUND));
    }

    // Тот же платёж пришёл повторно — параметры должны совпасть
    private Optional<Reservation> sameReservation(ReserveCommand cmd) {
        Optional<Reservation> existing = reservations.findByPaymentId(cmd.paymentId());
        existing.filter(r -> !r.sameRequest(cmd.userId(), cmd.accountId(), cmd.recipientAccountId(), cmd.amount(),
                cmd.commission())).ifPresent(r -> {
            throw new DomainException(RESERVATION_CONFLICT);
        });
        return existing;
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
                .forEach(id -> accounts.findByIdForUpdate(id).ifPresent(a -> locked.put(id, a)));
        return locked;
    }

    private static UsageCounters.Usage usageOf(Reservation r, Account source) {
        return new UsageCounters.Usage(r.getUserId(), r.getOperationType(), SourceKind.of(source.getType()),
                r.getAccountId(), r.getAmount(), r.getTotal(), r.getCreatedAt());
    }
}
