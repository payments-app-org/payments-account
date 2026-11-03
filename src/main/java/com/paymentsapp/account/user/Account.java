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
    private String cardNumber;
    private String cardLast4;
    private String paymentSystem;
    private boolean isDefault;
    private String cardExpiry;
    private String accountNumber;
    private String cardFingerprint;
    private OffsetDateTime createdAt;

    protected Account() {
    }

    // Привязка карты другого банка
    public static Account linkedCard(UUID userId, String last4, String paymentSystem, String expiry, String fingerprint) {
        Account a = new Account();
        a.id = UUID.randomUUID();
        a.userId = userId;
        a.type = AccountType.EXTERNAL_CARD;
        a.status = AccountStatus.ACTIVE;
        a.title = "Карта другого банка";
        a.balance = BigDecimal.ZERO;
        a.reserved = BigDecimal.ZERO;
        a.currency = "RUB";
        a.cardLast4 = last4;
        a.paymentSystem = paymentSystem;
        a.cardExpiry = expiry;
        a.cardFingerprint = fingerprint;
        a.createdAt = OffsetDateTime.now();
        return a;
    }

    // Деньги на привязанной карте другого банка мы не ведём: их списывает процессинг
    public boolean isExternal() {
        return type == AccountType.EXTERNAL_CARD;
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
