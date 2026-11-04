package com.paymentsapp.account.web;

import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountRepository;
import com.paymentsapp.account.user.AccountType;
import com.paymentsapp.account.user.User;
import com.paymentsapp.account.user.UserRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Внутренний API для payment: найти нашего клиента по реквизитам и карту, на которую зачислить перевод.
// 404 — реквизиты не наши (перевод уходит в другой банк).
@RestController
@RequestMapping("/api/internal/recipients")
public class RecipientController {

    private final UserRepository users;
    private final AccountRepository accounts;

    public RecipientController(UserRepository users, AccountRepository accounts) {
        this.users = users;
        this.accounts = accounts;
    }

    // По телефону деньги приходят на карту по умолчанию
    @GetMapping(params = "phone")
    public RecipientResponse byPhone(@RequestParam String phone) {
        User user = users.findByPhone(phone).orElseThrow(RecipientController::notFound);
        UUID accountId = accounts.findByUserIdAndIsDefaultTrue(user.getId()).map(Account::getId).orElse(null);
        // «Мой кошелёк» получателя — для перевода на кошелёк по номеру телефона
        UUID walletId = accounts.findByUserIdOrderByTypeDescCreatedAt(user.getId()).stream()
                .filter(a -> a.getType() == AccountType.WALLET).map(Account::getId).findFirst().orElse(null);
        return new RecipientResponse(user.getId(), user.displayName(), accountId, user.getSbpDefaultBankId(), walletId);
    }

    // По номеру карты — ровно на эту карту
    @GetMapping(params = "cardNumber")
    public RecipientResponse byCard(@RequestParam String cardNumber) {
        Account card = accounts.findByCardNumberAndType(cardNumber, AccountType.CARD).orElseThrow(RecipientController::notFound);
        User user = users.findById(card.getUserId()).orElseThrow();
        return new RecipientResponse(user.getId(), user.displayName(), card.getId(), user.getSbpDefaultBankId(), null);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Получатель не найден");
    }

    public record RecipientResponse(UUID userId, String displayName, UUID accountId, String sbpBankId, UUID walletAccountId) {
    }
}
