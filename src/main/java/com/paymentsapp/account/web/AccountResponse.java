package com.paymentsapp.account.web;

import com.paymentsapp.account.limits.Limits;
import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountStatus;
import com.paymentsapp.account.user.AccountType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
        // Номер счёта (20 цифр) — для справок; у карт других банков его нет
        String accountNumber,
        OffsetDateTime openedAt,
        Limits limits,
        // Свой счёт в другом банке (EXTERNAL_SBP): идентификатор банка-участника СБП
        String sbpBankId,
        // Кредитная карта — с неё нельзя пополнить кошелёк
        boolean credit
) {

    static AccountResponse of(Account a, Limits l, boolean defaultForPayments) {
        // Без ограничений — limits: null
        Limits limits = l == null || l.equals(Limits.NONE) ? null : l;
        // Баланс карты другого банка нам неизвестен
        return new AccountResponse(a.getId(), a.getType(), a.getStatus(), a.getTitle(), a.isExternal() ? null : a.getBalance(),
                a.available(), a.getCurrency(), a.getCardLast4(), a.getPaymentSystem(), a.isDefault(), defaultForPayments,
                a.getCardExpiry(), a.getAccountNumber(), a.getCreatedAt(), limits, a.getSbpBankId(), a.isCredit());
    }
}
