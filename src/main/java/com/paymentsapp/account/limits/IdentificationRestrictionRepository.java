package com.paymentsapp.account.limits;

import com.paymentsapp.account.user.IdentificationLevel;
import jakarta.persistence.QueryHint;
import java.util.List;
import org.hibernate.jpa.HibernateHints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;

public interface IdentificationRestrictionRepository extends JpaRepository<IdentificationRestriction, Integer> {

    // Кэш запросов Hibernate: таблица меняется только миграциями
    @QueryHints(@QueryHint(name = HibernateHints.HINT_CACHEABLE, value = "true"))
    List<IdentificationRestriction> findByLevel(IdentificationLevel level);
}
