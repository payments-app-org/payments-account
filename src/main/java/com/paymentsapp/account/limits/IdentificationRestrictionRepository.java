package com.paymentsapp.account.limits;

import com.paymentsapp.account.user.IdentificationLevel;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentificationRestrictionRepository extends JpaRepository<IdentificationRestriction, Integer> {

    List<IdentificationRestriction> findByLevel(IdentificationLevel level);
}
