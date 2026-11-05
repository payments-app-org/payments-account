package com.paymentsapp.account.support;

import com.paymentsapp.account.error.AccountError;
import com.paymentsapp.account.limits.IdentificationLimits;
import com.paymentsapp.account.limits.IdentificationRestriction;
import com.paymentsapp.account.limits.LimitPeriod;
import com.paymentsapp.account.limits.LimitSource;
import com.paymentsapp.account.limits.OperationLimit;
import com.paymentsapp.account.limits.OperationType;
import com.paymentsapp.account.limits.SourceKind;
import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountStatus;
import com.paymentsapp.account.user.AccountType;
import com.paymentsapp.account.user.IdentificationLevel;
import com.paymentsapp.account.user.User;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

// Сущности для тестов: у JPA-сущностей нет публичных конструкторов и сеттеров, поля ставим рефлексией
public final class TestData {

    public static final String RUB = "RUB";

    private TestData() {
    }

    public static User user(UUID id, IdentificationLevel level) {
        User u = BeanUtils.instantiateClass(User.class);
        ReflectionTestUtils.setField(u, "id", id);
        ReflectionTestUtils.setField(u, "identificationLevel", level);
        ReflectionTestUtils.setField(u, "firstName", "Тест");
        ReflectionTestUtils.setField(u, "lastName", "Тестов");
        return u;
    }

    public static Account account(UUID userId, AccountType type, String balance) {
        Account a = BeanUtils.instantiateClass(Account.class);
        ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(a, "userId", userId);
        ReflectionTestUtils.setField(a, "type", type);
        ReflectionTestUtils.setField(a, "status", AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(a, "currency", RUB);
        ReflectionTestUtils.setField(a, "balance", new BigDecimal(balance));
        ReflectionTestUtils.setField(a, "reserved", BigDecimal.ZERO);
        ReflectionTestUtils.setField(a, "createdAt", OffsetDateTime.now());
        return a;
    }

    public static Account creditCard(UUID userId, String balance) {
        Account a = account(userId, AccountType.CARD, balance);
        ReflectionTestUtils.setField(a, "credit", true);
        return a;
    }

    public static IdentificationRestriction restriction(IdentificationLevel level, OperationType operation, String group,
                                                        SourceKind source, AccountError error) {
        IdentificationRestriction r = BeanUtils.instantiateClass(IdentificationRestriction.class);
        ReflectionTestUtils.setField(r, "level", level);
        ReflectionTestUtils.setField(r, "operationType", operation);
        ReflectionTestUtils.setField(r, "providerGroup", group);
        ReflectionTestUtils.setField(r, "source", source);
        ReflectionTestUtils.setField(r, "errorCode", error);
        return r;
    }

    public static OperationLimit limit(OperationType operation, LimitSource source, LimitPeriod period, String min, String max) {
        OperationLimit l = BeanUtils.instantiateClass(OperationLimit.class);
        ReflectionTestUtils.setField(l, "operationType", operation);
        ReflectionTestUtils.setField(l, "source", source);
        ReflectionTestUtils.setField(l, "period", period);
        ReflectionTestUtils.setField(l, "minAmount", min == null ? null : new BigDecimal(min));
        ReflectionTestUtils.setField(l, "maxAmount", new BigDecimal(max));
        return l;
    }

    public static IdentificationLimits identificationLimits(IdentificationLevel level, String maxBalance, boolean receives) {
        IdentificationLimits l = BeanUtils.instantiateClass(IdentificationLimits.class);
        ReflectionTestUtils.setField(l, "level", level);
        ReflectionTestUtils.setField(l, "walletMaxBalance", new BigDecimal(maxBalance));
        ReflectionTestUtils.setField(l, "walletMaxMonthlyTurnover", new BigDecimal(maxBalance));
        ReflectionTestUtils.setField(l, "receivesTransfers", receives);
        return l;
    }
}
