package com.paymentsapp.account.web;

import com.paymentsapp.account.reservation.AccountLimits;
import com.paymentsapp.account.reservation.AccountLimitsRepository;
import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountRepository;
import com.paymentsapp.account.user.User;
import com.paymentsapp.account.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository users;
    private final AccountRepository accounts;
    private final AccountLimitsRepository limits;

    public UserController(UserRepository users, AccountRepository accounts, AccountLimitsRepository limits) {
        this.users = users;
        this.accounts = accounts;
        this.limits = limits;
    }

    @GetMapping("/{userId}")
    public UserResponse getUser(@PathVariable UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));
        return UserResponse.of(user, accountsOf(userId));
    }

    @GetMapping("/{userId}/accounts")
    public List<AccountResponse> getAccounts(@PathVariable UUID userId) {
        if (!users.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден");
        }
        return accountsOf(userId);
    }

    private List<AccountResponse> accountsOf(UUID userId) {
        List<Account> list = accounts.findByUserIdOrderByTypeDescCreatedAt(userId);
        Map<java.util.UUID, AccountLimits> byAccount = limits.findAllById(list.stream().map(Account::getId).toList())
                .stream().collect(Collectors.toMap(AccountLimits::getAccountId, Function.identity()));
        return list.stream().map(a -> AccountResponse.of(a, byAccount.get(a.getId()))).toList();
    }
}
