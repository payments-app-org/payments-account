package com.paymentsapp.account.limits;

import com.paymentsapp.account.user.IdentificationLevel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentificationLimitsRepository extends JpaRepository<IdentificationLimits, IdentificationLevel> {
}
