package com.paymentsapp.account.reservation.check;

import static com.paymentsapp.account.error.AccountError.*;

import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountStatus;
import com.paymentsapp.platform.error.DomainException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// Счёт списания принадлежит пользователю, активен и в валюте платежа
@Component
@Order(1)
public class SourceAccountCheck implements ReservationCheck {

    @Override
    public void check(ReservationContext ctx) {
        Account source = ctx.source();
        if (!source.getUserId().equals(ctx.command().userId())) {
            throw new DomainException(ACCOUNT_NOT_OWNED);
        }
        if (!source.isActive()) {
            throw new DomainException(source.getStatus() == AccountStatus.CLOSED ? CARD_UNLINKED : ACCOUNT_BLOCKED);
        }
        if (!source.getCurrency().equals(ctx.command().currency())) {
            throw new DomainException(CURRENCY_MISMATCH);
        }
    }
}
