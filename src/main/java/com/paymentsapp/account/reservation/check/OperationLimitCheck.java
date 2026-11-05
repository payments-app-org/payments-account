package com.paymentsapp.account.reservation.check;

import static com.paymentsapp.account.error.AccountError.*;

import com.paymentsapp.account.limits.LimitPeriod;
import com.paymentsapp.account.limits.LimitsService;
import com.paymentsapp.account.limits.OperationLimit;
import com.paymentsapp.account.limits.UsageCounters;
import com.paymentsapp.platform.error.DomainException;
import com.paymentsapp.platform.text.Rubles;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// Лимиты на вид операции (operation_limits): минимум и максимум одной операции, за день, за месяц.
// Израсходованное — из счётчиков UsageCounters, а не суммированием резервов.
@Component
@Order(4)
public class OperationLimitCheck implements ReservationCheck {

    private final LimitsService limits;
    private final UsageCounters usage;

    public OperationLimitCheck(LimitsService limits, UsageCounters usage) {
        this.limits = limits;
        this.usage = usage;
    }

    @Override
    public void check(ReservationContext ctx) {
        BigDecimal amount = ctx.command().amount();
        for (OperationLimit limit : limits.operationLimits(ctx.command().operationType())) {
            if (!limit.getSource().covers(ctx.sourceKind())) {
                continue;
            }
            switch (limit.getPeriod()) {
                case SINGLE -> checkSingle(limit, amount);
                case DAY -> checkPeriod(ctx, limit, amount, usage.today());
                case MONTH -> checkPeriod(ctx, limit, amount, usage.thisMonth());
            }
        }
    }

    private void checkSingle(OperationLimit limit, BigDecimal amount) {
        if (limit.getMinAmount() != null && amount.compareTo(limit.getMinAmount()) < 0) {
            throw new DomainException(BELOW_MIN_AMOUNT, Rubles.format(limit.getMinAmount()));
        }
        if (amount.compareTo(limit.getMaxAmount()) > 0) {
            throw new DomainException(SINGLE_LIMIT, Rubles.format(limit.getMaxAmount()));
        }
    }

    private void checkPeriod(ReservationContext ctx, OperationLimit limit, BigDecimal amount, LocalDate periodStart) {
        BigDecimal used = usage.operationUsed(ctx.command().userId(), limit.getOperationType(), limit.getSource(),
                limit.getPeriod(), periodStart);
        if (used.add(amount).compareTo(limit.getMaxAmount()) > 0) {
            BigDecimal left = limit.getMaxAmount().subtract(used).max(BigDecimal.ZERO);
            throw new DomainException(limit.getPeriod() == LimitPeriod.DAY ? DAY_LIMIT : MONTH_LIMIT,
                    Rubles.format(limit.getMaxAmount()), Rubles.format(left));
        }
    }
}
