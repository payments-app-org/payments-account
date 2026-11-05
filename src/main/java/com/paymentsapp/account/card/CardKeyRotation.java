package com.paymentsapp.account.card;

import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// При старте приводит номера наших карт к текущему ключу: открытые номера из старых миграций защищает
// (HMAC + шифр, открытый номер стирает), записи на старом ключе расшифровывает и защищает заново.
// Пачками в отдельных транзакциях, FOR UPDATE SKIP LOCKED: несколько подов делят работу, а не повторяют её.
// Отпечатки карт других банков (номера мы не храним) переводятся на новый ключ при следующей встрече с картой;
// пока такие записи есть, старый ключ удалять нельзя — об этом пишем в лог.
@Component
public class CardKeyRotation implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CardKeyRotation.class);
    private static final int BATCH = 100;

    private final AccountRepository accounts;
    private final CardNumberProtector protector;
    private final TransactionTemplate tx;

    public CardKeyRotation(AccountRepository accounts, CardNumberProtector protector, TransactionTemplate tx) {
        this.accounts = accounts;
        this.protector = protector;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        String key = protector.currentKeyId();
        int total = 0;
        int done;
        do {
            done = tx.execute(status -> rekey(accounts.lockCardsToRekey(key, BATCH), key));
            total += done;
        } while (done == BATCH);
        if (total > 0) {
            log.info("Номера {} карт защищены ключом {}", total, key);
        }
        long stale = accounts.countLinkedCardsOnOtherKey(key);
        if (stale > 0) {
            log.info("Карт других банков с отпечатком на старом ключе: {} — старый ключ пока нужен", stale);
        }
    }

    private int rekey(List<Account> cards, String key) {
        for (Account card : cards) {
            String pan = card.getCardNumber() != null ? card.getCardNumber()
                    : protector.decrypt(card.getCardKeyId(), card.getCardNumberEncrypted());
            card.protectCardNumber(protector.fingerprint(pan), protector.encrypt(pan), key);
        }
        return cards.size();
    }
}
