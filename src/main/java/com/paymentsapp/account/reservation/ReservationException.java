package com.paymentsapp.account.reservation;

import org.springframework.http.HttpStatus;

// Бизнес-отказ резервирования. code уходит в ответ — по нему payment решает, что сказать пользователю.
public class ReservationException extends RuntimeException {

    public enum Code {
        ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND),
        ACCOUNT_NOT_OWNED(HttpStatus.FORBIDDEN),
        ACCOUNT_BLOCKED(HttpStatus.UNPROCESSABLE_CONTENT),
        CURRENCY_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT),
        RECIPIENT_INVALID(HttpStatus.UNPROCESSABLE_CONTENT),
        INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_CONTENT),
        LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT),
        RECIPIENT_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT),
        RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND),
        RESERVATION_CONFLICT(HttpStatus.CONFLICT);

        final HttpStatus status;

        Code(HttpStatus status) {
            this.status = status;
        }
    }

    private final Code code;

    public ReservationException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() { return code; }
    public HttpStatus getStatus() { return code.status; }
}
