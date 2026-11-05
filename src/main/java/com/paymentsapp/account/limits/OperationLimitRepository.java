package com.paymentsapp.account.limits;

import jakarta.persistence.QueryHint;
import java.util.List;
import org.hibernate.jpa.HibernateHints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;

public interface OperationLimitRepository extends JpaRepository<OperationLimit, Integer> {

    @QueryHints(@QueryHint(name = HibernateHints.HINT_CACHEABLE, value = "true"))
    List<OperationLimit> findByOperationType(OperationType operationType);

    @QueryHints(@QueryHint(name = HibernateHints.HINT_CACHEABLE, value = "true"))
    List<OperationLimit> findAllByOrderById();
}
