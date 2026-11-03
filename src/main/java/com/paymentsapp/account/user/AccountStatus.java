package com.paymentsapp.account.user;

public enum AccountStatus {
    ACTIVE,
    BLOCKED,
    // Отвязанная карта другого банка: не показывается и не используется
    CLOSED
}
