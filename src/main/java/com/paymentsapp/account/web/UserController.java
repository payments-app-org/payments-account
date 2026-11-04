package com.paymentsapp.account.web;

import com.paymentsapp.account.card.CardLinkingService;
import com.paymentsapp.account.reservation.AccountLimits;
import com.paymentsapp.account.reservation.AccountLimitsRepository;
import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountRepository;
import com.paymentsapp.account.user.AccountStatus;
import com.paymentsapp.account.user.AccountType;
import com.paymentsapp.account.user.User;
import com.paymentsapp.account.user.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository users;
    private final AccountRepository accounts;
    private final AccountLimitsRepository limits;
    private final CardLinkingService cards;
    private final com.paymentsapp.account.reservation.LimitRuleRepository operationLimits;

    public UserController(UserRepository users, AccountRepository accounts, AccountLimitsRepository limits,
                          CardLinkingService cards, com.paymentsapp.account.reservation.LimitRuleRepository operationLimits) {
        this.users = users;
        this.accounts = accounts;
        this.limits = limits;
        this.cards = cards;
        this.operationLimits = operationLimits;
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

    // Привязать карту другого банка
    @PostMapping("/{userId}/linked-cards")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse linkCard(@PathVariable UUID userId, @RequestBody LinkCardRequest req) {
        Account card = cards.link(userId, req.cardNumber(), req.expiry(), req.cvc());
        return AccountResponse.of(card, limits.findById(card.getId()).orElse(null), false);
    }

    // Лимиты пользователя на виды операций — для подсказки «от 10 ₽ до …» в формах
    @GetMapping("/{userId}/operation-limits")
    public List<OperationLimit> operationLimits(@PathVariable UUID userId) {
        return operationLimits.findByUserId(userId).stream()
                .map(l -> new OperationLimit(l.getOperationType(), l.getPeriod().name(), l.getMaxAmount())).toList();
    }

    public record OperationLimit(String operationType, String period, java.math.BigDecimal maxAmount) {
    }

    // Отвязать карту другого банка
    @DeleteMapping("/{userId}/linked-cards/{accountId}")
    public List<AccountResponse> unlinkCard(@PathVariable UUID userId, @PathVariable UUID accountId) {
        cards.unlink(userId, accountId);
        return accountsOf(userId);
    }

    // «Сердечко»: этот счёт подставляется в «Списать с» по умолчанию
    @PutMapping("/{userId}/default-payment-account")
    public List<AccountResponse> setDefaultPaymentAccount(@PathVariable UUID userId, @RequestBody DefaultPaymentRequest req) {
        cards.setDefaultPaymentAccount(userId, req.accountId());
        return accountsOf(userId);
    }

    // Порядок: кошелёк, карты нашего банка, привязанные карты; внутри — по дате
    private static final Comparator<Account> ORDER = Comparator
            .comparing((Account a) -> a.getType() == AccountType.WALLET ? 0 : a.getType() == AccountType.CARD ? 1 : 2)
            .thenComparing(Account::getCreatedAt);

    private List<AccountResponse> accountsOf(UUID userId) {
        UUID paymentDefault = users.findById(userId).map(User::getDefaultPaymentAccountId).orElse(null);
        // Закрытые (отвязанные) карты не показываем
        List<Account> list = accounts.findByUserIdOrderByTypeDescCreatedAt(userId).stream()
                .filter(a -> a.getStatus() != AccountStatus.CLOSED).sorted(ORDER).toList();
        Map<java.util.UUID, AccountLimits> byAccount = limits.findAllById(list.stream().map(Account::getId).toList())
                .stream().collect(Collectors.toMap(AccountLimits::getAccountId, Function.identity()));
        return list.stream()
                .map(a -> AccountResponse.of(a, byAccount.get(a.getId()), a.getId().equals(paymentDefault)))
                .toList();
    }

    public record LinkCardRequest(String cardNumber, String expiry, String cvc) {
    }

    public record DefaultPaymentRequest(UUID accountId) {
    }
}
