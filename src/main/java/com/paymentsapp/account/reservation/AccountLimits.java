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

    public UUID getAccountId() { return accountId; }
    public BigDecimal getMaxPayment() { return maxPayment; }
    public BigDecimal getMaxBalance() { return maxBalance; }
    public BigDecimal getMaxMonthlyTurnover() { return maxMonthlyTurnover; }
}
