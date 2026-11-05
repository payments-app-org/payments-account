package com.paymentsapp.account.limits;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountLimitsRepository extends JpaRepository<AccountLimits, UUID> {
}
