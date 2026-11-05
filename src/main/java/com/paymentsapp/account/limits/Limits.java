package com.paymentsapp.account.limits;

import java.math.BigDecimal;

// Действующие лимиты счёта: платёж (вместе с комиссией), остаток, оборот за календарный месяц; null — без ограничения
public record Limits(BigDecimal maxPayment, BigDecimal maxBalance, BigDecimal maxMonthlyTurnover) {

    public static final Limits NONE = new Limits(null, null, null);

    static Limits of(IdentificationLimits wallet) {
        return new Limits(wallet.getWalletMaxPayment(), wallet.getWalletMaxBalance(), wallet.getWalletMaxMonthlyTurnover());
    }
}
