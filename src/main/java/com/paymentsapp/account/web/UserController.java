package com.paymentsapp.account.web;

import com.paymentsapp.account.card.CardLinkingService;
import com.paymentsapp.account.limits.Limits;
import com.paymentsapp.account.limits.LimitsService;
import com.paymentsapp.account.limits.OperationType;
import com.paymentsapp.account.profile.ProfileService;
import com.paymentsapp.account.profile.ProfileService.AccountsView;
import com.paymentsapp.account.profile.RoundupProperties;
import com.paymentsapp.account.profile.RoundupService;
import com.paymentsapp.account.profile.SbpSourceService;
import com.paymentsapp.account.user.Account;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Профиль, счета, привязка карт, «Округление на пользу», лимиты для форм. Логика — в сервисах.
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final ProfileService profiles;
    private final CardLinkingService cards;
    private final RoundupService roundup;
    private final RoundupProperties roundupOptions;
    private final SbpSourceService sbpSources;
    private final LimitsService limits;

    public UserController(ProfileService profiles, CardLinkingService cards, RoundupService roundup,
                          RoundupProperties roundupOptions, SbpSourceService sbpSources, LimitsService limits) {
        this.profiles = profiles;
        this.cards = cards;
        this.roundup = roundup;
        this.roundupOptions = roundupOptions;
        this.sbpSources = sbpSources;
        this.limits = limits;
    }

    @GetMapping("/{userId}")
    public UserResponse getUser(@PathVariable UUID userId) {
        AccountsView view = profiles.accounts(userId);
        return UserResponse.of(view.user(), accounts(view));
    }

    @GetMapping("/{userId}/accounts")
    public List<AccountResponse> getAccounts(@PathVariable UUID userId) {
        return accounts(profiles.accounts(userId));
    }

    // Привязать карту другого банка
    @PostMapping("/{userId}/linked-cards")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse linkCard(@PathVariable UUID userId, @RequestBody LinkCardRequest req) {
        Account card = cards.link(userId, req.cardNumber(), req.expiry(), req.cvc());
        AccountsView view = profiles.accounts(userId);
        return AccountResponse.of(card, view.limits().get(card.getId()), false);
    }

    // Отвязать карту другого банка
    @DeleteMapping("/{userId}/linked-cards/{accountId}")
    public List<AccountResponse> unlinkCard(@PathVariable UUID userId, @PathVariable UUID accountId) {
        cards.unlink(userId, accountId);
        return accounts(profiles.accounts(userId));
    }

    // «Сердечко»: этот счёт подставляется в «Списать с» по умолчанию
    @PutMapping("/{userId}/default-payment-account")
    public List<AccountResponse> setDefaultPaymentAccount(@PathVariable UUID userId, @RequestBody DefaultPaymentRequest req) {
        cards.setDefaultPaymentAccount(userId, req.accountId());
        return accounts(profiles.accounts(userId));
    }

    // Допустимые шаги «Округления на пользу» — для формы настройки
    @GetMapping("/roundup/options")
    public RoundupOptions roundupOptions() {
        return new RoundupOptions(roundupOptions.steps());
    }

    @PutMapping("/{userId}/roundup")
    public UserResponse.Roundup enableRoundup(@PathVariable UUID userId, @RequestBody RoundupRequest req) {
        return UserResponse.Roundup.of(roundup.enable(userId, req.step(), req.fundCode(), req.fundName()));
    }

    @DeleteMapping("/{userId}/roundup")
    public ResponseEntity<Void> disableRoundup(@PathVariable UUID userId) {
        roundup.disable(userId);
        return ResponseEntity.noContent().build();
    }

    // «Ваш счёт в другом банке» для автопополнения по СБП
    @PostMapping("/{userId}/sbp-sources")
    public AccountResponse sbpSource(@PathVariable UUID userId, @RequestBody SbpSourceRequest req) {
        return AccountResponse.of(sbpSources.findOrCreate(userId, req.bankId(), req.bankName()), Limits.NONE, false);
    }

    // Лимиты на виды операций — для подсказки «от 10 ₽ до …» в формах (одинаковы для всех клиентов)
    @GetMapping("/{userId}/operation-limits")
    public List<OperationLimitResponse> operationLimits(@PathVariable UUID userId) {
        return limits.allOperationLimits().stream().map(OperationLimitResponse::of).toList();
    }

    // Сколько пользователь потратил на операции этого вида с начала месяца — для бесплатного лимита СБП в pricing
    @GetMapping("/{userId}/operation-usage")
    public OperationUsage operationUsage(@PathVariable UUID userId, @RequestParam OperationType operationType) {
        return new OperationUsage(operationType, profiles.monthUsage(userId, operationType));
    }

    private static List<AccountResponse> accounts(AccountsView view) {
        UUID paymentDefault = view.user().getDefaultPaymentAccountId();
        return view.accounts().stream()
                .map(a -> AccountResponse.of(a, view.limits().get(a.getId()), a.getId().equals(paymentDefault)))
                .toList();
    }

    public record LinkCardRequest(String cardNumber, String expiry, String cvc) {
    }

    public record DefaultPaymentRequest(UUID accountId) {
    }

    public record RoundupRequest(Integer step, String fundCode, String fundName) {
    }

    public record RoundupOptions(List<Integer> steps) {
    }

    public record SbpSourceRequest(String bankId, String bankName) {
    }

    public record OperationUsage(OperationType operationType, BigDecimal month) {
    }
}
