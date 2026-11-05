package com.paymentsapp.account.reservation;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByPaymentId(UUID paymentId);

    // Есть ли незавершённые платежи со счёта
    boolean existsByAccountIdAndStatus(UUID accountId, ReservationStatus status);
}
