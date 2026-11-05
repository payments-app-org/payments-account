package com.paymentsapp.account.limits;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

// Лимиты конкретного счёта (карты); у «Моего кошелька» лимиты задаёт уровень идентификации владельца
@Entity
@Table(name = "account_limits")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class AccountLimits {

    @Id
    private UUID accountId;

    // null — без ограничения
    private BigDecimal maxPayment;
    private BigDecimal maxBalance;
    private BigDecimal maxMonthlyTurnover;

    protected AccountLimits() {
    }

    public static AccountLimits fromDefaults(UUID accountId, AccountLimitDefaults defaults) {
        AccountLimits l = new AccountLimits();
        l.accountId = accountId;
        l.maxPayment = defaults.getMaxPayment();
        l.maxBalance = defaults.getMaxBalance();
        l.maxMonthlyTurnover = defaults.getMaxMonthlyTurnover();
        return l;
    }

    public Limits toLimits() {
        return new Limits(maxPayment, maxBalance, maxMonthlyTurnover);
    }

    public UUID getAccountId() { return accountId; }
}
