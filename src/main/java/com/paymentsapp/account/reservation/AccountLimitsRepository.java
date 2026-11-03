package com.paymentsapp.account.reservation;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountLimitsRepository extends JpaRepository<AccountLimits, UUID> {
}
