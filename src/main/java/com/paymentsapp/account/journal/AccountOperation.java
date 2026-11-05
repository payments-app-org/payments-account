package com.paymentsapp.account.journal;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

// Запись журнала движений денег: что случилось со счётом и какой стал остаток. Записи не меняются.
@Entity
@Table(name = "account_operations")
@Immutable
public class AccountOperation {

    @Id
    private UUID id;

    private UUID accountId;
    private UUID paymentId;
    @Enumerated(EnumType.STRING)
    private OperationKind kind;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private OffsetDateTime createdAt;

    protected AccountOperation() {
    }

    AccountOperation(UUID accountId, UUID paymentId, OperationKind kind, BigDecimal amount, BigDecimal balanceAfter,
                     OffsetDateTime now) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.paymentId = paymentId;
        this.kind = kind;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.createdAt = now;
    }

    public UUID getAccountId() { return accountId; }
    public UUID getPaymentId() { return paymentId; }
    public OperationKind getKind() { return kind; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
}
