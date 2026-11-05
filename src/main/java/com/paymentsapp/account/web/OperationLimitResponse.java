package com.paymentsapp.account.web;

import com.paymentsapp.account.limits.LimitPeriod;
import com.paymentsapp.account.limits.LimitSource;
import com.paymentsapp.account.limits.OperationLimit;
import com.paymentsapp.account.limits.OperationType;
import java.math.BigDecimal;

public record OperationLimitResponse(OperationType operationType, LimitSource source, LimitPeriod period,
                                     BigDecimal minAmount, BigDecimal maxAmount) {

    static OperationLimitResponse of(OperationLimit l) {
        return new OperationLimitResponse(l.getOperationType(), l.getSource(), l.getPeriod(), l.getMinAmount(), l.getMaxAmount());
    }
}
