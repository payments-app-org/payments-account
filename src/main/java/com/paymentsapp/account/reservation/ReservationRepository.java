package com.paymentsapp.account.reservation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByPaymentId(UUID paymentId);

    // Есть ли незавершённые платежи со счёта
    boolean existsByAccountIdAndStatus(UUID accountId, ReservationStatus status);

    // Сколько лимита уже израсходовано: снятые резервы не считаются
    @Query("""
            select coalesce(sum(r.amount), 0) from Reservation r
            where r.userId = :userId and r.operationType = :operationType
              and r.status <> com.paymentsapp.account.reservation.ReservationStatus.RELEASED
              and r.createdAt >= :from
            """)
    BigDecimal usedSince(UUID userId, String operationType, OffsetDateTime from);

    // Сколько списано с этого счёта операциями этого вида с начала периода
    @Query("""
            select coalesce(sum(r.amount), 0) from Reservation r
            where r.accountId = :accountId and r.operationType = :operationType
              and r.status <> com.paymentsapp.account.reservation.ReservationStatus.RELEASED
              and r.createdAt >= :from
            """)
    BigDecimal accountUsedSince(UUID accountId, String operationType, OffsetDateTime from);

    // Оборот счёта: сколько списано и зарезервировано с него с начала периода
    @Query("""
            select coalesce(sum(r.total), 0) from Reservation r
            where r.accountId = :accountId
              and r.status <> com.paymentsapp.account.reservation.ReservationStatus.RELEASED
              and r.createdAt >= :from
            """)
    BigDecimal accountTurnoverSince(UUID accountId, OffsetDateTime from);
}
