package com.paymentsapp.account.limits;

import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountType;
import com.paymentsapp.account.user.IdentificationLevel;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

// Какие лимиты действуют на счёт: у «Моего кошелька» — по уровню идентификации владельца, у карт — свои
@Service
public class LimitsService {

    private final IdentificationLimitsRepository identification;
    private final AccountLimitsRepository accountLimits;
    private final OperationLimitRepository operationLimits;
    private final IdentificationRestrictionRepository restrictions;

    public LimitsService(IdentificationLimitsRepository identification, AccountLimitsRepository accountLimits,
                         OperationLimitRepository operationLimits, IdentificationRestrictionRepository restrictions) {
        this.identification = identification;
        this.accountLimits = accountLimits;
        this.operationLimits = operationLimits;
        this.restrictions = restrictions;
    }

    public Limits of(Account account, IdentificationLevel ownerLevel) {
        if (account.getType() == AccountType.WALLET) {
            return Limits.of(identification(ownerLevel));
        }
        return accountLimits.findById(account.getId()).map(AccountLimits::toLimits).orElse(Limits.NONE);
    }

    // Для списка счетов одного владельца — одним запросом лимиты всех карт
    public Map<UUID, Limits> of(Collection<Account> accounts, IdentificationLevel ownerLevel) {
        Map<UUID, Limits> cards = accountLimits.findAllById(accounts.stream().map(Account::getId).toList()).stream()
                .collect(Collectors.toMap(AccountLimits::getAccountId, AccountLimits::toLimits));
        Limits wallet = Limits.of(identification(ownerLevel));
        return accounts.stream().collect(Collectors.toMap(Account::getId,
                a -> a.getType() == AccountType.WALLET ? wallet : cards.getOrDefault(a.getId(), Limits.NONE),
                (a, b) -> a));
    }

    public IdentificationLimits identification(IdentificationLevel level) {
        return identification.findById(level)
                .orElseThrow(() -> new IllegalStateException("Нет лимитов для уровня идентификации " + level));
    }

    public List<OperationLimit> operationLimits(OperationType operationType) {
        return operationLimits.findByOperationType(operationType);
    }

    public List<OperationLimit> allOperationLimits() {
        return operationLimits.findAllByOrderById();
    }

    public List<IdentificationRestriction> restrictions(IdentificationLevel level) {
        return restrictions.findByLevel(level);
    }
}
