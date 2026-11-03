package com.paymentsapp.account.user;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    // Кошелёк первым, затем карты по дате оформления
    List<Account> findByUserIdOrderByTypeDescCreatedAt(UUID userId);
}
