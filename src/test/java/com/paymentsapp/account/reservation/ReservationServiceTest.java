package com.paymentsapp.account.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paymentsapp.account.error.AccountError;
import com.paymentsapp.account.limits.Limits;
import com.paymentsapp.account.limits.LimitsService;
import com.paymentsapp.account.limits.OperationType;
import com.paymentsapp.account.limits.UsageCounters;
import com.paymentsapp.account.reservation.check.ReservationCheck;
import com.paymentsapp.account.support.TestData;
import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountRepository;
import com.paymentsapp.account.user.AccountType;
import com.paymentsapp.account.user.IdentificationLevel;
import com.paymentsapp.account.user.User;
import com.paymentsapp.account.user.UserRepository;
import com.paymentsapp.platform.error.DomainException;
import com.paymentsapp.platform.outbox.Outbox;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReservationServiceTest {

    private static final String TOPIC = "account.reservations";

    private final UserRepository users = mock(UserRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final ReservationCheck check = mock(ReservationCheck.class);
    private final LimitsService limits = mock(LimitsService.class);
    private final UsageCounters usage = mock(UsageCounters.class);
    private final Outbox outbox = mock(Outbox.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T09:00:00Z"), ZoneId.of("Europe/Moscow"));
    private final ReservationService service = new ReservationService(users, accounts, reservations, List.of(check), limits,
            usage, outbox, clock, TOPIC);

    private final User user = TestData.user(UUID.randomUUID(), IdentificationLevel.FULL);
    private final Account card = TestData.account(user.getId(), AccountType.CARD, "10000");

    @BeforeEach
    void stubs() {
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(accounts.findByIdForUpdate(card.getId())).thenReturn(Optional.of(card));
        when(limits.of(card, IdentificationLevel.FULL)).thenReturn(Limits.NONE);
    }

    private ReserveCommand command(String amount) {
        return new ReserveCommand(UUID.randomUUID(), user.getId(), card.getId(), null, OperationType.SBP_PHONE,
                new BigDecimal(amount), new BigDecimal("50"), TestData.RUB, null);
    }

    @Test
    void reserveHoldsFundsCountsLimitsAndPublishes() {
        ReserveCommand cmd = command("1000");

        Reservation r = service.reserve(cmd);

        assertThat(r.getTotal()).isEqualByComparingTo("1050");
        assertThat(card.available()).isEqualByComparingTo("8950");
        ArgumentCaptor<UsageCounters.Usage> counted = ArgumentCaptor.forClass(UsageCounters.Usage.class);
        verify(usage).add(counted.capture());
        assertThat(counted.getValue().amount()).isEqualByComparingTo("1000");
        assertThat(counted.getValue().total()).isEqualByComparingTo("1050");
        verify(outbox).add(eq(TOPIC), eq(cmd.paymentId().toString()), eq("FundsReserved"), anyMap());
    }

    @Test
    void failedCheckReservesNothing() {
        doThrow(new DomainException(AccountError.INSUFFICIENT_FUNDS)).when(check).check(any());

        assertThatThrownBy(() -> service.reserve(command("1000"))).isInstanceOf(DomainException.class);
        assertThat(card.available()).isEqualByComparingTo("10000");
        verify(usage, never()).add(any());
        verify(outbox, never()).add(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void repeatedRequestReturnsSameReservationAndDifferentOneConflicts() {
        ReserveCommand cmd = command("1000");
        Reservation first = service.reserve(cmd);
        when(reservations.findByPaymentId(cmd.paymentId())).thenReturn(Optional.of(first));

        assertThat(service.reserve(cmd)).isSameAs(first);
        ReserveCommand other = new ReserveCommand(cmd.paymentId(), cmd.userId(), cmd.accountId(), null, cmd.operationType(),
                new BigDecimal("999"), cmd.commission(), cmd.currency(), null);
        assertThatThrownBy(() -> service.reserve(other)).hasMessage(AccountError.RESERVATION_CONFLICT.template());
    }

    @Test
    void releaseReturnsFundsAndLimit() {
        ReserveCommand cmd = command("1000");
        Reservation r = service.reserve(cmd);
        when(reservations.findByPaymentId(cmd.paymentId())).thenReturn(Optional.of(r));

        service.release(cmd.paymentId());

        assertThat(card.available()).isEqualByComparingTo("10000");
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        verify(usage).subtract(any());
        assertThatThrownBy(() -> service.capture(cmd.paymentId())).hasMessage(AccountError.RESERVATION_RELEASED.template());
    }
}
