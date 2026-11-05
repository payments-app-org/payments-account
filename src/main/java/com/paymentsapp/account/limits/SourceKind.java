package com.paymentsapp.account.limits;

import com.paymentsapp.account.user.AccountType;

// Откуда списываем — для лимитов и запретов: электронные деньги («Мой кошелёк») или счёт / карта
public enum SourceKind {
    WALLET,
    CARD;

    public static SourceKind of(AccountType type) {
        return type == AccountType.WALLET ? WALLET : CARD;
    }
}
