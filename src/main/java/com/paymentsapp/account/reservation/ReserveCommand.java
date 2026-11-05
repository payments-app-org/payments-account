package com.paymentsapp.account.reservation;

import com.paymentsapp.account.limits.OperationType;
import java.math.BigDecimal;
import java.util.UUID;

public record ReserveCommand(
        UUID paymentId,
        UUID userId,
        UUID accountId,
        UUID recipientAccountId,
        OperationType operationType,
        BigDecimal amount,
        BigDecimal commission,
        String currency,
        // Группа поставщика для BILL: от неё зависит, доступна ли оплата на уровне идентификации
        String providerGroup
) {

    public BigDecimal total() {
        return amount.add(commission);
    }
}
