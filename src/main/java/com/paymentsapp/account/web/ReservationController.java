package com.paymentsapp.account.web;

import com.paymentsapp.account.limits.OperationType;
import com.paymentsapp.account.reservation.Reservation;
import com.paymentsapp.account.reservation.ReservationService;
import com.paymentsapp.account.reservation.ReservationStatus;
import com.paymentsapp.account.reservation.ReserveCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Внутренний API для сервиса payment: резерв → списание или снятие резерва
@RestController
@RequestMapping("/api/internal/reservations")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping
    public ReservationResponse reserve(@Valid @RequestBody ReserveRequest req) {
        return ReservationResponse.of(service.reserve(new ReserveCommand(req.paymentId(), req.userId(),
                req.accountId(), req.recipientAccountId(), req.operationType(), req.amount(), req.commission(), req.currency(),
                req.providerGroup())));
    }

    @PostMapping("/{paymentId}/release")
    public ReservationResponse release(@PathVariable UUID paymentId) {
        return ReservationResponse.of(service.release(paymentId));
    }

    @PostMapping("/{paymentId}/capture")
    public ReservationResponse capture(@PathVariable UUID paymentId) {
        return ReservationResponse.of(service.capture(paymentId));
    }

    public record ReserveRequest(
            @NotNull UUID paymentId,
            @NotNull UUID userId,
            @NotNull UUID accountId,
            UUID recipientAccountId,
            @NotNull OperationType operationType,
            @NotNull @Positive @Digits(integer = 15, fraction = 2) BigDecimal amount,
            @NotNull @PositiveOrZero @Digits(integer = 15, fraction = 2) BigDecimal commission,
            @NotBlank String currency,
            String providerGroup
    ) {
    }

    public record ReservationResponse(UUID reservationId, UUID paymentId, ReservationStatus status, BigDecimal total) {

        static ReservationResponse of(Reservation r) {
            return new ReservationResponse(r.getId(), r.getPaymentId(), r.getStatus(), r.getTotal());
        }
    }
}
