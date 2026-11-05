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

    boolean existsByUserIdAndCardFingerprintAndStatusNot(UUID userId, String cardFingerprint, AccountStatus status);

    long countByUserIdAndTypeAndStatusNot(UUID userId, AccountType type, AccountStatus status);

    // Поиск нашей карты по HMAC номера: открытых номеров в базе нет
    Optional<Account> findByCardNumberHashAndType(String cardNumberHash, AccountType type);

    // Карты, номера которых ещё не защищены (CardNumberMigration)
    List<Account> findByCardNumberIsNotNull();

    Optional<Account> findByUserIdAndTypeAndSbpBankId(UUID userId, AccountType type, String sbpBankId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(UUID id);
}
