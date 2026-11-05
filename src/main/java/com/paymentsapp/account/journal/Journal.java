package com.paymentsapp.account.journal;

import com.paymentsapp.account.user.Account;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Пишет движение денег в журнал — в той же транзакции, что меняет баланс (иначе журнал и баланс разойдутся).
// Деньги на картах других банков мы не ведём — по ним записей нет.
@Component
public class Journal {

    private final AccountOperationRepository operations;

    public Journal(AccountOperationRepository operations) {
        this.operations = operations;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void debit(Account account, UUID paymentId, BigDecimal amount, OffsetDateTime now) {
        record(account, paymentId, OperationKind.DEBIT, amount, now);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void credit(Account account, UUID paymentId, BigDecimal amount, OffsetDateTime now) {
        record(account, paymentId, OperationKind.CREDIT, amount, now);
    }

    @Transactional(readOnly = true)
    public boolean credited(UUID paymentId, UUID accountId) {
        return operations.existsByPaymentIdAndAccountIdAndKind(paymentId, accountId, OperationKind.CREDIT);
    }

    private void record(Account account, UUID paymentId, OperationKind kind, BigDecimal amount, OffsetDateTime now) {
        if (account.isExternal()) {
            return;
        }
        operations.save(new AccountOperation(account.getId(), paymentId, kind, amount, account.getBalance(), now));
    }
}
