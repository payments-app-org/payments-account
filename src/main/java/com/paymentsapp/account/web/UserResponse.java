package com.paymentsapp.account.web;

import com.paymentsapp.account.user.Subscription;
import com.paymentsapp.account.user.User;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String firstName,
        String lastName,
        String middleName,
        String displayName,
        String phone,
        String email,
        Subscription subscription,
        String sbpDefaultBankId,
        UUID defaultPaymentAccountId,
        String identificationLevel,
        List<AccountResponse> accounts,
        // «Округление на пользу»; null — выключено
        Roundup roundup
) {

    public record Roundup(int step, String fundCode, String fundName, OffsetDateTime enabledAt) {

        static Roundup of(User u) {
            return u.getRoundupStep() == null ? null
                    : new Roundup(u.getRoundupStep(), u.getRoundupFundCode(), u.getRoundupFundName(), u.getRoundupEnabledAt());
        }
    }


    static UserResponse of(User u, List<AccountResponse> accounts) {
        return new UserResponse(u.getId(), u.getFirstName(), u.getLastName(), u.getMiddleName(), u.displayName(),
                u.getPhone(), u.getEmail(), u.getSubscription(), u.getSbpDefaultBankId(), u.getDefaultPaymentAccountId(), u.getIdentificationLevel().name(), accounts,
                Roundup.of(u));
    }
}
