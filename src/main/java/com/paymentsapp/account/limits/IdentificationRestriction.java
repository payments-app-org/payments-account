package com.paymentsapp.account.limits;

import com.paymentsapp.account.error.AccountError;
import com.paymentsapp.account.user.IdentificationLevel;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

// Что запрещено на уровне идентификации: вид операции (+ группа поставщика для BILL, + источник списания).
// Текст отказа — по коду из AccountError.
@Entity
@Table(name = "identification_restrictions")
@Immutable
public class IdentificationRestriction {

    @Id
    private Integer id;

    @Enumerated(EnumType.STRING)
    private IdentificationLevel level;

    @Enumerated(EnumType.STRING)
    private OperationType operationType;

    // null — любая группа
    private String providerGroup;

    // null — с любого источника
    @Enumerated(EnumType.STRING)
    private SourceKind source;

    @Enumerated(EnumType.STRING)
    private AccountError errorCode;

    protected IdentificationRestriction() {
    }

    public boolean matches(OperationType operation, String group, SourceKind from) {
        return operationType == operation
                && (providerGroup == null || providerGroup.equals(group))
                && (source == null || source == from);
    }

    public IdentificationLevel getLevel() { return level; }
    public AccountError getErrorCode() { return errorCode; }
}
