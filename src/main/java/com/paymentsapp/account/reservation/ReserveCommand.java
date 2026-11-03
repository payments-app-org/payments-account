package com.paymentsapp.account.reservation;

import java.math.BigDecimal;
import java.util.UUID;

public record ReserveCommand(
        UUID paymentId,
        UUID userId,
        UUID accountId,
        String operationType,
        BigDecimal amount,
        BigDecimal commission,
        String currency
) {
}
