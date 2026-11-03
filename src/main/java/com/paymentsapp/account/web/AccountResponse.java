package com.paymentsapp.account.web;

import com.paymentsapp.account.reservation.AccountLimits;
import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountStatus;
import com.paymentsapp.account.user.AccountType;
import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        AccountType type,
        AccountStatus status,
        String title,
        BigDecimal balance,
        BigDecimal available,
        String currency,
        String cardLast4,
        String paymentSystem,
        // Карта по умолчанию для входящих переводов
        boolean isDefault,
        // Инструмент оплаты по умолчанию — подставляется в «Списать с»
        boolean defaultForPayments,
        String cardExpiry,
        Limits limits
) {

    static AccountResponse of(Account a, AccountLimits l, boolean defaultForPayments) {
        Limits limits = l == null ? null : new Limits(l.getMaxPayment(), l.getMaxBalance(), l.getMaxMonthlyTurnover());
        // Баланс карты другого банка нам неизвестен
        return new AccountResponse(a.getId(), a.getType(), a.getStatus(), a.getTitle(), a.isExternal() ? null : a.getBalance(),
                a.available(), a.getCurrency(), a.getCardLast4(), a.getPaymentSystem(), a.isDefault(), defaultForPayments,
                a.getCardExpiry(), limits);
    }

    // null в поле — без ограничения
    public record Limits(BigDecimal maxPayment, BigDecimal maxBalance, BigDecimal maxMonthlyTurnover) {
    }
}
