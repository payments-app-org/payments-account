package com.paymentsapp.account.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paymentsapp.account.error.AccountError;
import com.paymentsapp.account.limits.Limits;
import com.paymentsapp.account.limits.LimitsService;
import com.paymentsapp.account.support.TestData;
import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountRepository;
import com.paymentsapp.account.user.AccountType;
import com.paymentsapp.account.user.IdentificationLevel;
import com.paymentsapp.account.user.User;
import com.paymentsapp.account.user.UserRepository;
import com.paymentsapp.platform.error.DomainException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreditServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final LimitsService limits = mock(LimitsService.class);
    private final Journal journal = mock(Journal.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T09:00:00Z"), ZoneId.of("Europe/Moscow"));
    private final CreditService service = new CreditService(users, accounts, limits, journal, clock);

    // Кошелёк без идентификации: остаток не больше 15 000 ₽
    private final User anonymous = TestData.user(UUID.randomUUID(), IdentificationLevel.ANONYMOUS);
    private final Account wallet = TestData.account(anonymous.getId(), AccountType.WALLET, "14000");

    @BeforeEach
    void stubs() {
        when(users.findById(anonymous.getId())).thenReturn(Optional.of(anonymous));
        when(accounts.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(limits.of(wallet, IdentificationLevel.ANONYMOUS)).thenReturn(limitsWithMaxBalance("15000"));
    }

    @Test
    void creditOverWalletMaxBalanceIsDeclined() {
        UUID payment = UUID.randomUUID();

        assertThatThrownBy(() -> service.credit(payment, anonymous.getId(), wallet.getId(), new BigDecimal("2000"), "RUB"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo(AccountError.RECIPIENT_MAX_BALANCE);
        assertThat(wallet.getBalance()).isEqualByComparingTo("14000");
        verify(journal, never()).credit(any(), any(), any(), any());
    }

    // Повтор запроса СБП с тем же платежом: в журнале уже есть зачисление — второй раз не зачисляем
    @Test
    void repeatedCreditIsNotAppliedTwice() {
        UUID payment = UUID.randomUUID();
        when(journal.credited(payment, wallet.getId())).thenReturn(false, true);

        service.credit(payment, anonymous.getId(), wallet.getId(), new BigDecimal("500"), "RUB");
        service.credit(payment, anonymous.getId(), wallet.getId(), new BigDecimal("500"), "RUB");

        assertThat(wallet.getBalance()).isEqualByComparingTo("14500");
    }

    @Test
    void foreignAccountIsRejected() {
        User other = TestData.user(UUID.randomUUID(), IdentificationLevel.FULL);
        when(users.findById(other.getId())).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.credit(UUID.randomUUID(), other.getId(), wallet.getId(), BigDecimal.TEN, "RUB"))
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo(AccountError.ACCOUNT_NOT_OWNED);
    }

    private static Limits limitsWithMaxBalance(String max) {
        return new Limits(null, new BigDecimal(max), null);
    }
}
