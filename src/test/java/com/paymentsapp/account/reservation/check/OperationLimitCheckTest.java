package com.paymentsapp.account.reservation.check;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.paymentsapp.account.limits.LimitPeriod;
import com.paymentsapp.account.limits.LimitSource;
import com.paymentsapp.account.limits.Limits;
import com.paymentsapp.account.limits.LimitsService;
import com.paymentsapp.account.limits.OperationType;
import com.paymentsapp.account.limits.UsageCounters;
import com.paymentsapp.account.support.TestData;
import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountType;
import com.paymentsapp.account.user.IdentificationLevel;
import com.paymentsapp.account.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OperationLimitCheckTest {

    private static final LocalDate MONTH = LocalDate.of(2026, 11, 1);

    private final LimitsService limits = mock(LimitsService.class);
    private final UsageCounters usage = mock(UsageCounters.class);
    private final OperationLimitCheck check = new OperationLimitCheck(limits, usage);
    private final User user = TestData.user(UUID.randomUUID(), IdentificationLevel.FULL);
    private final Account wallet = TestData.account(user.getId(), AccountType.WALLET, "1000000");
    private final Account card = TestData.account(user.getId(), AccountType.CARD, "1000000");

    @BeforeEach
    void limits() {
        when(usage.thisMonth()).thenReturn(MONTH);
        when(limits.operationLimits(OperationType.SBP_PHONE)).thenReturn(List.of(
                TestData.limit(OperationType.SBP_PHONE, LimitSource.ANY, LimitPeriod.SINGLE, "10", "1000000"),
                TestData.limit(OperationType.SBP_PHONE, LimitSource.ANY, LimitPeriod.MONTH, null, "3000000")));
        when(limits.operationLimits(OperationType.ABROAD)).thenReturn(List.of(
                TestData.limit(OperationType.ABROAD, LimitSource.WALLET, LimitPeriod.SINGLE, null, "300000"),
                TestData.limit(OperationType.ABROAD, LimitSource.CARD, LimitPeriod.SINGLE, null, "400000")));
    }

    @Test
    void belowMinimumIsRejected() {
        assertThatThrownBy(() -> check.check(Contexts.of(user, card, null, OperationType.SBP_PHONE, "5", null, Limits.NONE)))
                .hasMessage("Минимальная сумма перевода — 10 ₽");
    }

    @Test
    void monthLimitUsesCounters() {
        when(usage.operationUsed(eq(user.getId()), eq(OperationType.SBP_PHONE), eq(LimitSource.ANY), eq(LimitPeriod.MONTH), any()))
                .thenReturn(new BigDecimal("2999000"));
        assertThatThrownBy(() -> check.check(Contexts.of(user, card, null, OperationType.SBP_PHONE, "2000", null, Limits.NONE)))
                .hasMessageContaining("Превышен месячный лимит").hasMessageContaining("доступно ещё 1 000 ₽");
    }

    @Test
    void abroadLimitDependsOnSource() {
        assertThatThrownBy(() -> check.check(Contexts.of(user, wallet, null, OperationType.ABROAD, "350000", null, Limits.NONE)))
                .hasMessageContaining("300 000 ₽");
        assertThatCode(() -> check.check(Contexts.of(user, card, null, OperationType.ABROAD, "350000", null, Limits.NONE)))
                .doesNotThrowAnyException();
    }
}
