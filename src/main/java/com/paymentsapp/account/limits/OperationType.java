package com.paymentsapp.account.limits;

// Вид операции из payment (PaymentType). Своя копия перечня: сервисы договариваются через API.
public enum OperationType {
    SBP_PHONE,
    CARD2CARD,
    OWN_TRANSFER,
    ABROAD,
    QR,
    WALLET_TRANSFER,
    REQUISITES,
    REQUISITES_OUR,
    BILL
}
