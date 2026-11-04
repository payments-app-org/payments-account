package com.paymentsapp.account.user;

public enum AccountType {
    WALLET,
    // Карта нашего банка
    CARD,
    // Привязанная карта другого банка: только источник списания, баланс нам неизвестен
    EXTERNAL_CARD,
    // Свой счёт в другом банке для пополнения по СБП: банк-участник и телефон владельца, баланс неизвестен
    EXTERNAL_SBP
}
