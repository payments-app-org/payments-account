package com.paymentsapp.account.web;

import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountType;
import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        AccountType type,
        String title,
        BigDecimal balance,
        String currency,
        String cardLast4,
        String paymentSystem
) {

    static AccountResponse of(Account a) {
        return new AccountResponse(a.getId(), a.getType(), a.getTitle(), a.getBalance(),
                a.getCurrency(), a.getCardLast4(), a.getPaymentSystem());
    }
}
