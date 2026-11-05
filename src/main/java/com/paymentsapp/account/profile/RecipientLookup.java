package com.paymentsapp.account.profile;

import static com.paymentsapp.account.error.AccountError.RECIPIENT_UNKNOWN;

import com.paymentsapp.account.card.CardNumberProtector;
import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountRepository;
import com.paymentsapp.account.user.AccountType;
import com.paymentsapp.account.user.User;
import com.paymentsapp.account.user.UserRepository;
import com.paymentsapp.platform.error.DomainException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Наш ли клиент получатель и куда зачислять: по телефону — на карту по умолчанию (и его «Мой кошелёк»
// для перевода на кошелёк), по номеру карты — ровно на эту карту (ищем по HMAC номера)
@Service
public class RecipientLookup {

    private final UserRepository users;
    private final AccountRepository accounts;
    private final CardNumberProtector cards;

    public RecipientLookup(UserRepository users, AccountRepository accounts, CardNumberProtector cards) {
        this.users = users;
        this.accounts = accounts;
        this.cards = cards;
    }

    @Transactional(readOnly = true)
    public Recipient byPhone(String phone) {
        User user = users.findByPhone(phone).orElseThrow(() -> new DomainException(RECIPIENT_UNKNOWN));
        UUID cardId = accounts.findByUserIdAndIsDefaultTrue(user.getId()).map(Account::getId).orElse(null);
        UUID walletId = accounts.findByUserIdOrderByTypeDescCreatedAt(user.getId()).stream()
                .filter(Account::isWallet).map(Account::getId).findFirst().orElse(null);
        return new Recipient(user.getId(), user.displayName(), cardId, user.getSbpDefaultBankId(), walletId);
    }

    @Transactional(readOnly = true)
    public Recipient byCard(String cardNumber) {
        Account card = accounts.findFirstByCardNumberHashInAndType(cards.fingerprints(cardNumber), AccountType.CARD)
                .orElseThrow(() -> new DomainException(RECIPIENT_UNKNOWN));
        User user = users.findById(card.getUserId()).orElseThrow();
        return new Recipient(user.getId(), user.displayName(), card.getId(), user.getSbpDefaultBankId(), null);
    }

    public record Recipient(UUID userId, String displayName, UUID accountId, String sbpBankId, UUID walletAccountId) {
    }
}
