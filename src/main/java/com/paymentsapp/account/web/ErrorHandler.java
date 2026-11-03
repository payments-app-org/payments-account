package com.paymentsapp.account.web;

import com.paymentsapp.account.reservation.ReservationException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler(ReservationException.class)
    ProblemDetail reservationFailed(ReservationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.getStatus(), e.getMessage());
        problem.setProperty("code", e.getCode().name());
        return problem;
    }
}
