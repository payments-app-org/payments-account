package com.paymentsapp.account.limits;

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

// Лимит на вид операции: на одну операцию (с минимумом), за день или за месяц; для всех источников или
// отдельно для кошелька и карт (переводы за рубеж). Одинаков для всех клиентов.
@Entity
@Table(name = "operation_limits")
@Immutable
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
public class OperationLimit {

    @Id
    private Integer id;

    @Enumerated(EnumType.STRING)
    private OperationType operationType;

    @Enumerated(EnumType.STRING)
    private LimitSource source;

    @Enumerated(EnumType.STRING)
    private LimitPeriod period;

    // Только для SINGLE; null — без минимума
    private BigDecimal minAmount;
    private BigDecimal maxAmount;

    protected OperationLimit() {
    }

    public OperationType getOperationType() { return operationType; }
    public LimitSource getSource() { return source; }
    public LimitPeriod getPeriod() { return period; }
    public BigDecimal getMinAmount() { return minAmount; }
    public BigDecimal getMaxAmount() { return maxAmount; }
}
