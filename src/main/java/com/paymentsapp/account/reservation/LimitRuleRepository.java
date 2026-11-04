package com.paymentsapp.account.reservation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LimitRuleRepository extends JpaRepository<LimitRule, UUID> {

    List<LimitRule> findByUserIdAndOperationType(UUID userId, String operationType);

    List<LimitRule> findByUserId(UUID userId);
}
