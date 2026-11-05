package com.paymentsapp.account.limits;

import com.paymentsapp.account.user.AccountType;
import jakarta.persistence.Cacheable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;

// Лимиты, которые получает новый счёт этого типа (привязанная карта другого банка)
@Entity
@Table(name = "account_limit_defaults")
@Immutable
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
public class AccountLimitDefaults {

    @Id
    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    private BigDecimal maxPayment;
    private BigDecimal maxBalance;
    private BigDecimal maxMonthlyTurnover;

    protected AccountLimitDefaults() {
    }

    public BigDecimal getMaxPayment() { return maxPayment; }
    public BigDecimal getMaxBalance() { return maxBalance; }
    public BigDecimal getMaxMonthlyTurnover() { return maxMonthlyTurnover; }
}
