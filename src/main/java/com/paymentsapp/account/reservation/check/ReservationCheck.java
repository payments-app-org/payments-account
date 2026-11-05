package com.paymentsapp.account.reservation.check;

// Одно правило резерва. Нарушение — DomainException с кодом AccountError. Порядок проверок — @Order:
// сначала счёт списания, потом идентификация, получатель, лимиты, деньги.
public interface ReservationCheck {

    void check(ReservationContext context);
}
