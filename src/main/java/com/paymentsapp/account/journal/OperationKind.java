package com.paymentsapp.account.journal;

// Вид записи журнала: начальный остаток (деньги до появления журнала), списание, зачисление
public enum OperationKind {
    OPENING,
    DEBIT,
    CREDIT
}
