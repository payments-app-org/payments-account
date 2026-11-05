package com.paymentsapp.account.journal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountOperationRepository extends JpaRepository<AccountOperation, UUID> {

    boolean existsByPaymentIdAndAccountIdAndKind(UUID paymentId, UUID accountId, OperationKind kind);
}
