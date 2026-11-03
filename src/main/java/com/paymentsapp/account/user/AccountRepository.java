package com.paymentsapp.account.user;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    // Кошелёк первым, затем карты по дате оформления
    List<Account> findByUserIdOrderByTypeDescCreatedAt(UUID userId);

    Optional<Account> findByUserIdAndIsDefaultTrue(UUID userId);

    boolean existsByUserIdAndCardFingerprint(UUID userId, String cardFingerprint);

    long countByUserIdAndType(UUID userId, AccountType type);

    Optional<Account> findByCardNumberAndType(String cardNumber, AccountType type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(UUID id);
}
