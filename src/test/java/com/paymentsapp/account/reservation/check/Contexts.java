package com.paymentsapp.account.reservation.check;

import com.paymentsapp.account.limits.Limits;
import com.paymentsapp.account.limits.OperationType;
import com.paymentsapp.account.reservation.ReserveCommand;
import com.paymentsapp.account.support.TestData;
import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.User;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

final class Contexts {

    private Contexts() {
    }

    static ReservationContext of(User user, Account source, Account recipient, OperationType operation, String amount,
                                 String providerGroup, Limits limits) {
        ReserveCommand cmd = new ReserveCommand(UUID.randomUUID(), user.getId(), source.getId(),
                recipient == null ? null : recipient.getId(), operation, new BigDecimal(amount), BigDecimal.ZERO,
                TestData.RUB, providerGroup);
        return new ReservationContext(cmd, user, source, recipient, limits, OffsetDateTime.now());
    }
}
