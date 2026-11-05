package com.paymentsapp.account.reservation.check;

import static com.paymentsapp.account.error.AccountError.*;
import static com.paymentsapp.account.user.IdentificationLevel.*;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.paymentsapp.account.limits.Limits;
import com.paymentsapp.account.limits.LimitsService;
import com.paymentsapp.account.limits.OperationType;
import com.paymentsapp.account.limits.SourceKind;
import com.paymentsapp.account.support.TestData;
import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountType;
import com.paymentsapp.account.user.User;
import com.paymentsapp.platform.error.DomainException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentificationCheckTest {

    private final LimitsService limits = mock(LimitsService.class);
    private final IdentificationCheck check = new IdentificationCheck(limits);

    @Test
    void anonymousCannotTransferToPeople() {
        when(limits.restrictions(ANONYMOUS)).thenReturn(List.of(
                TestData.restriction(ANONYMOUS, OperationType.SBP_PHONE, null, null, PERSON_TRANSFER_NOT_IDENTIFIED)));
        User user = TestData.user(UUID.randomUUID(), ANONYMOUS);
        Account wallet = TestData.account(user.getId(), AccountType.WALLET, "1000");

        assertThatThrownBy(() -> check.check(Contexts.of(user, wallet, null, OperationType.SBP_PHONE, "100", null, Limits.NONE)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo(PERSON_TRANSFER_NOT_IDENTIFIED);
    }

    @Test
    void simplifiedRestrictionAppliesOnlyToWalletSource() {
        when(limits.restrictions(SIMPLIFIED)).thenReturn(List.of(
                TestData.restriction(SIMPLIFIED, OperationType.BILL, "EMONEY", SourceKind.WALLET, EMONEY_REQUIRES_FULL)));
        User user = TestData.user(UUID.randomUUID(), SIMPLIFIED);
        Account wallet = TestData.account(user.getId(), AccountType.WALLET, "1000");
        Account card = TestData.account(user.getId(), AccountType.EXTERNAL_CARD, "0");

        assertThatThrownBy(() -> check.check(Contexts.of(user, wallet, null, OperationType.BILL, "100", "EMONEY", Limits.NONE)))
                .hasMessageContaining("полной идентификацией");
        assertThatCode(() -> check.check(Contexts.of(user, card, null, OperationType.BILL, "100", "EMONEY", Limits.NONE)))
                .doesNotThrowAnyException();
        assertThatCode(() -> check.check(Contexts.of(user, wallet, null, OperationType.BILL, "100", "MOBILE", Limits.NONE)))
                .doesNotThrowAnyException();
    }
}
