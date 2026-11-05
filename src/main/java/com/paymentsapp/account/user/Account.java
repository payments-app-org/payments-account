package com.paymentsapp.account.user;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// Счёт пользователя: «Мой кошелёк» есть у всех, карты — у тех, кто их оформил.
// balance — реальные деньги, reserved — заблокированные под платежи в процессе.
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;

    private UUID userId;

    @Enumerated(EnumType.STRING)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    private String title;
    private BigDecimal balance;
    private BigDecimal reserved;
    private String currency;
    // Номер нашей карты открыто больше не храним: после CardNumberMigration поле пустое
    private String cardNumber;
    // HMAC номера (поиск получателя по карте) и номер, зашифрованный AES-GCM
    private String cardNumberHash;
    private String cardNumberEncrypted;
    private String cardLast4;
    private String sbpBankId;
    private String paymentSystem;
    private boolean isDefault;
    private String cardExpiry;
    private String accountNumber;
    private String cardFingerprint;
    // Кредитная карта: с неё нельзя пополнять электронные кошельки (161-ФЗ, ст. 7 ч. 9)
    private boolean credit;
    private OffsetDateTime createdAt;

    protected Account() {
    }

    static final String CURRENCY_RUB = "RUB";
    static final String LINKED_CARD_TITLE = "Карта другого банка";
    static final String SBP_SOURCE_TITLE = "Счёт в %s";

    // Привязка карты другого банка
    public static Account linkedCard(UUID userId, String last4, String paymentSystem, String expiry, String fingerprint,
                                     OffsetDateTime now) {
        Account a = new Account();
        a.id = UUID.randomUUID();
        a.userId = userId;
        a.type = AccountType.EXTERNAL_CARD;
        a.status = AccountStatus.ACTIVE;
        a.title = LINKED_CARD_TITLE;
        a.balance = BigDecimal.ZERO;
        a.reserved = BigDecimal.ZERO;
        a.currency = CURRENCY_RUB;
        a.cardLast4 = last4;
        a.paymentSystem = paymentSystem;
        a.cardExpiry = expiry;
        a.cardFingerprint = fingerprint;
        a.createdAt = now;
        return a;
    }

    // Свой счёт в другом банке — источник автопополнения по СБП
    public static Account sbpSource(UUID userId, String bankId, String bankName, OffsetDateTime now) {
        Account a = new Account();
        a.id = UUID.randomUUID();
        a.userId = userId;
        a.type = AccountType.EXTERNAL_SBP;
        a.status = AccountStatus.ACTIVE;
        a.title = SBP_SOURCE_TITLE.formatted(bankName);
        a.balance = BigDecimal.ZERO;
        a.reserved = BigDecimal.ZERO;
        a.currency = CURRENCY_RUB;
        a.sbpBankId = bankId;
        a.createdAt = now;
        return a;
    }

    // Номер карты: только отпечаток для поиска и шифротекст, открытый номер стираем
    public void protectCardNumber(String hash, String encrypted) {
        this.cardNumberHash = hash;
        this.cardNumberEncrypted = encrypted;
        this.cardNumber = null;
    }

    public boolean isCredit() {
        return credit;
    }

    public boolean isWallet() {
        return type == AccountType.WALLET;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    // Деньги на привязанной карте и на своём счёте в другом банке мы не ведём: их списывает процессинг
    public boolean isExternal() {
        return type == AccountType.EXTERNAL_CARD || type == AccountType.EXTERNAL_SBP;
    }

    public String getSbpBankId() {
        return sbpBankId;
    }

    // null — баланс неизвестен (карта другого банка)
    public BigDecimal available() {
        return isExternal() ? null : balance.subtract(reserved);
    }

    public void reserve(BigDecimal total) {
        if (isExternal()) {
            return;
        }
        if (available().compareTo(total) < 0) {
            throw new IllegalStateException("Недостаточно средств на счёте " + id);
        }
        reserved = reserved.add(total);
    }

    public void release(BigDecimal total) {
        if (isExternal()) {
            return;
        }
        reserved = reserved.subtract(total);
    }

    // Резерв превращается в списание: уходит и из reserved, и из balance
    public void capture(BigDecimal total) {
        if (isExternal()) {
            return;
        }
        reserved = reserved.subtract(total);
        balance = balance.subtract(total);
    }

    public void close() {
        status = AccountStatus.CLOSED;
    }

    // Входящий перевод от другого клиента
    // На привязанную карту другого банка деньги доставляет процессинг — у нас её баланс не ведём
    public void credit(BigDecimal amount) {
        if (isExternal()) {
            return;
        }
        balance = balance.add(amount);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public AccountType getType() { return type; }
    public AccountStatus getStatus() { return status; }
    public String getTitle() { return title; }
    public BigDecimal getBalance() { return balance; }
    public BigDecimal getReserved() { return reserved; }
    public String getCurrency() { return currency; }
    public String getCardNumber() { return cardNumber; }
    public String getCardLast4() { return cardLast4; }
    public String getPaymentSystem() { return paymentSystem; }
    public boolean isDefault() { return isDefault; }
    public String getCardExpiry() { return cardExpiry; }
    public String getAccountNumber() { return accountNumber; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
