package com.paymentsapp.account.reservation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByPaymentId(UUID paymentId);

    // Сколько лимита уже израсходовано: снятые резервы не считаются
    @Query("""
            select coalesce(sum(r.amount), 0) from Reservation r
            where r.userId = :userId and r.operationType = :operationType
              and r.status <> com.paymentsapp.account.reservation.ReservationStatus.RELEASED
              and r.createdAt >= :from
            """)
    BigDecimal usedSince(UUID userId, String operationType, OffsetDateTime from);
}
