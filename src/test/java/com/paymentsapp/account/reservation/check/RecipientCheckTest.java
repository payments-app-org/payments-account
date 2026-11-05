package com.paymentsapp.account.reservation.check;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.paymentsapp.account.limits.Limits;
import com.paymentsapp.account.limits.LimitsService;
import com.paymentsapp.account.limits.OperationType;
import com.paymentsapp.account.support.TestData;
import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountType;
import com.paymentsapp.account.user.IdentificationLevel;
import com.paymentsapp.account.user.User;
import com.paymentsapp.account.user.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecipientCheckTest {

    private final UserRepository users = mock(UserRepository.class);
    private final LimitsService limits = mock(LimitsService.class);
    private final RecipientCheck check = new RecipientCheck(users, limits);
    private final User sender = TestData.user(UUID.randomUUID(), IdentificationLevel.FULL);

    private User recipientOwner(IdentificationLevel level, boolean receives) {
        User owner = TestData.user(UUID.randomUUID(), level);
        when(users.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(users.findById(sender.getId())).thenReturn(Optional.of(sender));
        when(limits.identification(level)).thenReturn(TestData.identificationLimits(level, "15000", receives));
        when(limits.identification(IdentificationLevel.FULL))
                .thenReturn(TestData.identificationLimits(IdentificationLevel.FULL, "600000", true));
        return owner;
    }

    @Test
    void creditCardCannotTopUpWallet() {
        recipientOwner(IdentificationLevel.FULL, true);
        Account credit = TestData.creditCard(sender.getId(), "50000");
        Account ownWallet = TestData.account(sender.getId(), AccountType.WALLET, "0");
        when(limits.of(any(Account.class), any())).thenReturn(Limits.NONE);

        assertThatThrownBy(() -> check.check(Contexts.of(sender, credit, ownWallet, OperationType.OWN_TRANSFER, "1000", null, Limits.NONE)))
                .hasMessage("Пополнить кошелёк с кредитной карты нельзя");
    }

    @Test
    void anonymousWalletDoesNotReceiveTransfersFromOthers() {
        User anonymous = recipientOwner(IdentificationLevel.ANONYMOUS, false);
        Account wallet = TestData.account(sender.getId(), AccountType.WALLET, "5000");
        Account theirs = TestData.account(anonymous.getId(), AccountType.WALLET, "0");

        assertThatThrownBy(() -> check.check(Contexts.of(sender, wallet, theirs, OperationType.WALLET_TRANSFER, "100", null, Limits.NONE)))
                .hasMessageContaining("не прошёл идентификацию");
    }

    @Test
    void recipientMaxBalanceIsChecked() {
        User owner = recipientOwner(IdentificationLevel.SIMPLIFIED, true);
        Account wallet = TestData.account(sender.getId(), AccountType.WALLET, "50000");
        Account theirs = TestData.account(owner.getId(), AccountType.WALLET, "59000");
        when(limits.of(theirs, IdentificationLevel.SIMPLIFIED)).thenReturn(new Limits(null, new BigDecimal("60000"), null));

        assertThatThrownBy(() -> check.check(Contexts.of(sender, wallet, theirs, OperationType.WALLET_TRANSFER, "2000", null, Limits.NONE)))
                .hasMessage("Превышен максимальный остаток на счёте получателя: 60 000 ₽");
        assertThatCode(() -> check.check(Contexts.of(sender, wallet, theirs, OperationType.WALLET_TRANSFER, "1000", null, Limits.NONE)))
                .doesNotThrowAnyException();
    }
}
