package com.paymentsapp.account.user;

import jakarta.persistence.LockModeType;
import java.util.Collection;
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

    // Карта уже привязана: ищем по отпечаткам всеми ключами (запись могла быть сделана старым)
    Optional<Account> findFirstByUserIdAndCardFingerprintInAndStatusNot(UUID userId, Collection<String> fingerprints,
                                                                        AccountStatus status);

    long countByUserIdAndTypeAndStatusNot(UUID userId, AccountType type, AccountStatus status);

    // Поиск нашей карты по HMAC номера (всеми ключами): открытых номеров в базе нет
    Optional<Account> findFirstByCardNumberHashInAndType(Collection<String> cardNumberHashes, AccountType type);

    // Наши карты, которые нужно защитить текущим ключом: открытый номер или запись на другом ключе
    @Query(value = """
            select * from accounts where card_number is not null
               or (card_number_hash is not null and card_key_id is distinct from :key)
            limit :batch for update skip locked
            """, nativeQuery = true)
    List<Account> lockCardsToRekey(String key, int batch);

    @Query(value = "select count(*) from accounts where card_fingerprint is not null and fingerprint_key_id is distinct from :key",
            nativeQuery = true)
    long countLinkedCardsOnOtherKey(String key);

    Optional<Account> findByUserIdAndTypeAndSbpBankId(UUID userId, AccountType type, String sbpBankId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(UUID id);
}
