package com.paymentsapp.account.reservation;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

// Лимиты конкретного счёта; null — без ограничения
@Entity
@Table(name = "account_limits")
public class AccountLimits {

    @Id
    private UUID accountId;

    private BigDecimal maxPayment;
    private BigDecimal maxBalance;
    private BigDecimal maxMonthlyTurnover;

    protected AccountLimits() {
    }

    public AccountLimits(UUID accountId, BigDecimal maxPayment, BigDecimal maxBalance, BigDecimal maxMonthlyTurnover) {
        this.accountId = accountId;
        this.maxPayment = maxPayment;
        this.maxBalance = maxBalance;
        this.maxMonthlyTurnover = maxMonthlyTurnover;
    }

    // Лимиты «Моего кошелька» задаёт уровень идентификации владельца
    public static AccountLimits forWallet(UUID accountId, com.paymentsapp.account.user.IdentificationLevel level) {
        return new AccountLimits(accountId, level.walletMaxPayment(), level.walletMaxBalance(), level.walletMaxMonthlyTurnover());
    }

    public UUID getAccountId() { return accountId; }
    public BigDecimal getMaxPayment() { return maxPayment; }
    public BigDecimal getMaxBalance() { return maxBalance; }
    public BigDecimal getMaxMonthlyTurnover() { return maxMonthlyTurnover; }
}
