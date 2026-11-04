package com.paymentsapp.account.reservation;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "limits")
public class LimitRule {

    @Id
    private UUID id;

    private UUID userId;
    private String operationType;

    @Enumerated(EnumType.STRING)
    private LimitPeriod period;

    private BigDecimal maxAmount;

    protected LimitRule() {
    }

    public LimitRule(UUID userId, String operationType, LimitPeriod period, BigDecimal maxAmount) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.operationType = operationType;
        this.period = period;
        this.maxAmount = maxAmount;
    }

    public String getOperationType() { return operationType; }
    public LimitPeriod getPeriod() { return period; }
    public BigDecimal getMaxAmount() { return maxAmount; }
}
