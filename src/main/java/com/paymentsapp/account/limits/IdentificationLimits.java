package com.paymentsapp.account.limits;

import com.paymentsapp.account.user.IdentificationLevel;
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

// Лимиты «Моего кошелька» по уровню идентификации (161-ФЗ). Справочник меняется только миграциями — L2-кэш.
@Entity
@Table(name = "identification_limits")
@Immutable
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
public class IdentificationLimits {

    @Id
    @Enumerated(EnumType.STRING)
    private IdentificationLevel level;

    // null — без ограничения
    private BigDecimal walletMaxPayment;
    private BigDecimal walletMaxBalance;
    private BigDecimal walletMaxMonthlyTurnover;
    // Принимает ли кошелёк переводы от других людей (без идентификации — нет)
    private boolean receivesTransfers;

    protected IdentificationLimits() {
    }

    public IdentificationLevel getLevel() { return level; }
    public BigDecimal getWalletMaxPayment() { return walletMaxPayment; }
    public BigDecimal getWalletMaxBalance() { return walletMaxBalance; }
    public BigDecimal getWalletMaxMonthlyTurnover() { return walletMaxMonthlyTurnover; }
    public boolean receivesTransfers() { return receivesTransfers; }
}
