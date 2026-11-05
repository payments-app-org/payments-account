package com.paymentsapp.account.web;

import com.paymentsapp.account.journal.CreditService;
import com.paymentsapp.account.limits.OperationType;
import com.paymentsapp.account.reservation.Reservation;
import com.paymentsapp.account.reservation.ReservationService;
import com.paymentsapp.account.reservation.ReservationStatus;
import com.paymentsapp.account.reservation.ReserveCommand;
import com.paymentsapp.account.user.Account;
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

// Внутренний API для сервиса payment: резерв → списание или снятие резерва; перевод внутри банка одной операцией;
// зачисление входящего перевода
@RestController
@RequestMapping("/api/internal")
public class ReservationController {

    private final ReservationService service;
    private final CreditService credits;

    public ReservationController(ReservationService service, CreditService credits) {
        this.service = service;
        this.credits = credits;
    }

    @PostMapping("/reservations")
    public ReservationResponse reserve(@Valid @RequestBody ReserveRequest req) {
        return ReservationResponse.of(service.reserve(req.command()));
    }

    // Между своими счетами и в кошелёк другого клиента: проверки, списание и зачисление одной транзакцией
    @PostMapping("/transfers")
    public ReservationResponse transfer(@Valid @RequestBody ReserveRequest req) {
        return ReservationResponse.of(service.transfer(req.command()));
    }

    @PostMapping("/credits")
    public CreditResponse credit(@Valid @RequestBody CreditRequest req) {
        Account account = credits.credit(req.paymentId(), req.userId(), req.accountId(), req.amount(), req.currency());
        return new CreditResponse(req.paymentId(), account.getId());
    }

    @PostMapping("/reservations/{paymentId}/release")
    public ReservationResponse release(@PathVariable UUID paymentId) {
        return ReservationResponse.of(service.release(paymentId));
    }

    @PostMapping("/reservations/{paymentId}/capture")
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

        ReserveCommand command() {
            return new ReserveCommand(paymentId, userId, accountId, recipientAccountId, operationType, amount, commission,
                    currency, providerGroup);
        }
    }

    public record CreditRequest(
            @NotNull UUID paymentId,
            @NotNull UUID userId,
            @NotNull UUID accountId,
            @NotNull @Positive @Digits(integer = 15, fraction = 2) BigDecimal amount,
            @NotBlank String currency
    ) {
    }

    public record CreditResponse(UUID paymentId, UUID accountId) {
    }

    public record ReservationResponse(UUID reservationId, UUID paymentId, ReservationStatus status, BigDecimal total) {

        static ReservationResponse of(Reservation r) {
            return new ReservationResponse(r.getId(), r.getPaymentId(), r.getStatus(), r.getTotal());
        }
    }
}
