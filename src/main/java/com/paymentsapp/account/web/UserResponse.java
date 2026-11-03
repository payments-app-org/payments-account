package com.paymentsapp.account.web;

import com.paymentsapp.account.user.Subscription;
import com.paymentsapp.account.user.User;
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
        java.util.UUID defaultPaymentAccountId,
        String identificationLevel,
        List<AccountResponse> accounts
) {

    static UserResponse of(User u, List<AccountResponse> accounts) {
        return new UserResponse(u.getId(), u.getFirstName(), u.getLastName(), u.getMiddleName(), u.displayName(),
                u.getPhone(), u.getEmail(), u.getSubscription(), u.getSbpDefaultBankId(), u.getDefaultPaymentAccountId(), u.getIdentificationLevel(), accounts);
    }
}
