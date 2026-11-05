package com.paymentsapp.account.profile;

import static com.paymentsapp.account.error.AccountError.*;

import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountRepository;
import com.paymentsapp.account.user.AccountType;
import com.paymentsapp.account.user.UserRepository;
import com.paymentsapp.platform.error.DomainException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// «Ваш счёт в другом банке» для автопополнения по СБП: находим или заводим счёт-источник этого банка
@Service
public class SbpSourceService {

    // Идентификатор участника СБП — 12 цифр
    private static final Pattern SBP_BANK_ID = Pattern.compile("\\d{12}");

    private final UserRepository users;
    private final AccountRepository accounts;
    private final Clock clock;

    public SbpSourceService(UserRepository users, AccountRepository accounts, Clock clock) {
        this.users = users;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional
    public Account findOrCreate(UUID userId, String bankId, String bankName) {
        if (!users.existsById(userId)) {
            throw new DomainException(USER_NOT_FOUND);
        }
        if (bankId == null || !SBP_BANK_ID.matcher(bankId).matches() || bankName == null || bankName.isBlank()) {
            throw new DomainException(SBP_BANK_REQUIRED);
        }
        return accounts.findByUserIdAndTypeAndSbpBankId(userId, AccountType.EXTERNAL_SBP, bankId)
                .orElseGet(() -> accounts.save(Account.sbpSource(userId, bankId, bankName.trim(), OffsetDateTime.now(clock))));
    }
}
