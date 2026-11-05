package com.paymentsapp.account.reservation.check;

import com.paymentsapp.account.limits.LimitsService;
import com.paymentsapp.platform.error.DomainException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// Запреты по уровню идентификации (identification_restrictions): без идентификации — переводы людям и за рубеж,
// с упрощённой — с кошелька на другие кошельки и т. д.
@Component
@Order(2)
public class IdentificationCheck implements ReservationCheck {

    private final LimitsService limits;

    public IdentificationCheck(LimitsService limits) {
        this.limits = limits;
    }

    @Override
    public void check(ReservationContext ctx) {
        limits.restrictions(ctx.user().getIdentificationLevel()).stream()
                .filter(r -> r.matches(ctx.command().operationType(), ctx.command().providerGroup(), ctx.sourceKind()))
                .findFirst()
                .ifPresent(r -> {
                    throw new DomainException(r.getErrorCode());
                });
    }
}
