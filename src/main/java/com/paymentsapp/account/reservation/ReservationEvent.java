package com.paymentsapp.account.reservation;

// События резерва в топике account.reservations
enum ReservationEvent {
    FUNDS_RESERVED("FundsReserved"),
    FUNDS_RELEASED("FundsReleased"),
    FUNDS_CAPTURED("FundsCaptured");

    private final String type;

    ReservationEvent(String type) {
        this.type = type;
    }

    String type() {
        return type;
    }
}
