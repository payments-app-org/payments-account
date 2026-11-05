package com.paymentsapp.account.reservation.check;

import static com.paymentsapp.account.error.AccountError.INSUFFICIENT_FUNDS;

import com.paymentsapp.account.user.Account;
import com.paymentsapp.platform.error.DomainException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// Хватает ли денег. С карты другого банка деньги списывает процессинг — её баланс мы не проверяем.
@Component
@Order(6)
public class FundsCheck implements ReservationCheck {

    @Override
    public void check(ReservationContext ctx) {
        Account source = ctx.source();
        if (!source.isExternal() && source.available().compareTo(ctx.command().total()) < 0) {
            throw new DomainException(INSUFFICIENT_FUNDS);
        }
    }
}
