package com.paymentsapp.account.user;

public enum AccountType {
    WALLET,
    // Карта нашего банка
    CARD,
    // Привязанная карта другого банка: только источник списания, баланс нам неизвестен
    EXTERNAL_CARD
}
