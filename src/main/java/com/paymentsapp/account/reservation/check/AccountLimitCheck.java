package com.paymentsapp.account.reservation.check;

import static com.paymentsapp.account.error.AccountError.*;

import com.paymentsapp.account.limits.Limits;
import com.paymentsapp.account.limits.UsageCounters;
import com.paymentsapp.platform.error.DomainException;
import com.paymentsapp.platform.text.Rubles;
import java.math.BigDecimal;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// Лимиты счёта списания (вместе с комиссией): максимальный платёж и оборот за календарный месяц
@Component
@Order(5)
public class AccountLimitCheck implements ReservationCheck {

    private final UsageCounters usage;

    public AccountLimitCheck(UsageCounters usage) {
        this.usage = usage;
    }

    @Override
    public void check(ReservationContext ctx) {
        Limits limits = ctx.sourceLimits();
        BigDecimal total = ctx.command().total();
        if (limits.maxPayment() != null && total.compareTo(limits.maxPayment()) > 0) {
            throw new DomainException(ACCOUNT_MAX_PAYMENT, Rubles.format(limits.maxPayment()));
        }
        if (limits.maxMonthlyTurnover() != null) {
            BigDecimal used = usage.accountMonthUsed(ctx.source().getId(), usage.thisMonth());
            if (used.add(total).compareTo(limits.maxMonthlyTurnover()) > 0) {
                BigDecimal left = limits.maxMonthlyTurnover().subtract(used).max(BigDecimal.ZERO);
                throw new DomainException(ACCOUNT_MONTH_TURNOVER, Rubles.format(limits.maxMonthlyTurnover()), Rubles.format(left));
            }
        }
    }
}
