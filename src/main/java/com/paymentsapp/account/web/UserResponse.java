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
        String phone,
        String email,
        Subscription subscription,
        List<AccountResponse> accounts
) {

    static UserResponse of(User u, List<AccountResponse> accounts) {
        return new UserResponse(u.getId(), u.getFirstName(), u.getLastName(), u.getMiddleName(),
                u.getPhone(), u.getEmail(), u.getSubscription(), accounts);
    }
}
