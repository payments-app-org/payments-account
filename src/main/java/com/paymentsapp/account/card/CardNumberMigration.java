package com.paymentsapp.account.card;

import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Защищает номера карт, записанные открыто (старые миграции с тестовыми картами): HMAC + шифр, открытый номер стирается.
// Идемпотентна: на следующих запусках таких карт уже нет. Ключи — в коде приложения, поэтому не Liquibase.
@Component
public class CardNumberMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CardNumberMigration.class);

    private final AccountRepository accounts;
    private final CardNumberProtector protector;

    public CardNumberMigration(AccountRepository accounts, CardNumberProtector protector) {
        this.accounts = accounts;
        this.protector = protector;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Account> open = accounts.findByCardNumberIsNotNull();
        for (Account card : open) {
            card.protectCardNumber(protector.fingerprint(card.getCardNumber()), protector.encrypt(card.getCardNumber()));
        }
        if (!open.isEmpty()) {
            log.info("Защищены номера {} карт: открытых номеров в базе больше нет", open.size());
        }
    }
}
