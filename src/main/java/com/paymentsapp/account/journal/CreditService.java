package com.paymentsapp.account.journal;

import static com.paymentsapp.account.error.AccountError.*;

import com.paymentsapp.account.limits.LimitsService;
import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountRepository;
import com.paymentsapp.account.user.User;
import com.paymentsapp.account.user.UserRepository;
import com.paymentsapp.platform.error.DomainException;
import com.paymentsapp.platform.text.Rubles;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Зачисление входящего перевода (СБП из другого банка). Деньги уже пришли в банк — резерва нет, есть только
// проверки счёта получателя. Идемпотентно по paymentId: запись в журнале с этим платежом — значит, уже зачислено.
@Service
public class CreditService {

    private final UserRepository users;
    private final AccountRepository accounts;
    private final LimitsService limits;
    private final Journal journal;
    private final Clock clock;

    public CreditService(UserRepository users, AccountRepository accounts, LimitsService limits, Journal journal,
                         Clock clock) {
        this.users = users;
        this.accounts = accounts;
        this.limits = limits;
        this.journal = journal;
        this.clock = clock;
    }

    @Transactional
    public Account credit(UUID paymentId, UUID userId, UUID accountId, BigDecimal amount, String currency) {
        User user = users.findById(userId).orElseThrow(() -> new DomainException(USER_NOT_FOUND));
        Account account = accounts.findByIdForUpdate(accountId).orElseThrow(() -> new DomainException(ACCOUNT_NOT_FOUND));
        if (journal.credited(paymentId, accountId)) {
            return account;
        }
        if (!account.getUserId().equals(userId)) {
            throw new DomainException(ACCOUNT_NOT_OWNED);
        }
        if (!account.isActive() || account.isExternal()) {
            throw new DomainException(RECIPIENT_BLOCKED);
        }
        if (!account.getCurrency().equals(currency)) {
            throw new DomainException(CURRENCY_MISMATCH);
        }
        // Кошелёк без идентификации ограничен по остатку (161-ФЗ): лишнее зачислить нельзя — СБП вернёт отправителю
        BigDecimal maxBalance = limits.of(account, user.getIdentificationLevel()).maxBalance();
        if (maxBalance != null && account.getBalance().add(amount).compareTo(maxBalance) > 0) {
            throw new DomainException(RECIPIENT_MAX_BALANCE, Rubles.format(maxBalance));
        }
        account.credit(amount);
        journal.credit(account, paymentId, amount, OffsetDateTime.now(clock));
        return account;
    }
}
