package com.paymentsapp.account.reservation.check;

import com.paymentsapp.account.limits.Limits;
import com.paymentsapp.account.limits.SourceKind;
import com.paymentsapp.account.reservation.ReserveCommand;
import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.User;
import java.time.OffsetDateTime;

// Всё, что нужно проверкам резерва: команда, заблокированные пользователь и счёт списания, счёт получателя
// (null — внешний получатель или не найден) и действующие лимиты счёта списания
public record ReservationContext(ReserveCommand command, User user, Account source, Account recipient,
                                 Limits sourceLimits, OffsetDateTime now) {

    public SourceKind sourceKind() {
        return SourceKind.of(source.getType());
    }

    public boolean hasRecipient() {
        return command.recipientAccountId() != null;
    }
}
