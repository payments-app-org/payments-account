package com.paymentsapp.account.profile;

import static com.paymentsapp.account.error.AccountError.USER_NOT_FOUND;

import com.paymentsapp.account.limits.LimitPeriod;
import com.paymentsapp.account.limits.LimitSource;
import com.paymentsapp.account.limits.Limits;
import com.paymentsapp.account.limits.LimitsService;
import com.paymentsapp.account.limits.OperationType;
import com.paymentsapp.account.limits.UsageCounters;
import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountRepository;
import com.paymentsapp.account.user.AccountStatus;
import com.paymentsapp.account.user.User;
import com.paymentsapp.account.user.UserRepository;
import com.paymentsapp.platform.error.DomainException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Профиль и счета пользователя с действующими лимитами; расход по виду операции за месяц
@Service
public class ProfileService {

    // Порядок — как у констант AccountType (кошелёк, карты нашего банка, привязанные), внутри — по дате
    private static final Comparator<Account> ORDER = Comparator.comparing(Account::getType).thenComparing(Account::getCreatedAt);

    private final UserRepository users;
    private final AccountRepository accounts;
    private final LimitsService limits;
    private final UsageCounters usage;

    public ProfileService(UserRepository users, AccountRepository accounts, LimitsService limits, UsageCounters usage) {
        this.users = users;
        this.accounts = accounts;
        this.limits = limits;
        this.usage = usage;
    }

    @Transactional(readOnly = true)
    public User user(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new DomainException(USER_NOT_FOUND));
    }

    // Открытые счета (отвязанные карты не показываем) и их лимиты
    @Transactional(readOnly = true)
    public AccountsView accounts(UUID userId) {
        User user = user(userId);
        List<Account> list = accounts.findByUserIdOrderByTypeDescCreatedAt(userId).stream()
                .filter(a -> a.getStatus() != AccountStatus.CLOSED).sorted(ORDER).toList();
        return new AccountsView(user, list, limits.of(list, user.getIdentificationLevel()));
    }

    // Сколько пользователь потратил на операции этого вида с начала месяца — pricing считает по этому бесплатный остаток
    @Transactional(readOnly = true)
    public BigDecimal monthUsage(UUID userId, OperationType operation) {
        if (!users.existsById(userId)) {
            throw new DomainException(USER_NOT_FOUND);
        }
        return usage.operationUsed(userId, operation, LimitSource.ANY, LimitPeriod.MONTH, usage.thisMonth());
    }

    public record AccountsView(User user, List<Account> accounts, Map<UUID, Limits> limits) {
    }
}
