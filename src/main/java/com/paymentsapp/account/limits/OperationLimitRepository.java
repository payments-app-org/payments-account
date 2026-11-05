package com.paymentsapp.account.limits;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationLimitRepository extends JpaRepository<OperationLimit, Integer> {

    List<OperationLimit> findByOperationType(OperationType operationType);

    List<OperationLimit> findAllByOrderById();
}
