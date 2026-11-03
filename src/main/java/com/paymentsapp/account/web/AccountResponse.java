package com.paymentsapp.account.web;

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
        boolean isDefault
) {

    static AccountResponse of(Account a) {
        return new AccountResponse(a.getId(), a.getType(), a.getStatus(), a.getTitle(), a.getBalance(),
                a.available(), a.getCurrency(), a.getCardLast4(), a.getPaymentSystem(), a.isDefault());
    }
}
