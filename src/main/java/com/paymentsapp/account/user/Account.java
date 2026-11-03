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
    private OffsetDateTime createdAt;

    protected Account() {
    }

    public BigDecimal available() {
        return balance.subtract(reserved);
    }

    public void reserve(BigDecimal total) {
        if (available().compareTo(total) < 0) {
            throw new IllegalStateException("Недостаточно средств на счёте " + id);
        }
        reserved = reserved.add(total);
    }

    public void release(BigDecimal total) {
        reserved = reserved.subtract(total);
    }

    // Резерв превращается в списание: уходит и из reserved, и из balance
    public void capture(BigDecimal total) {
        reserved = reserved.subtract(total);
        balance = balance.subtract(total);
    }

    // Входящий перевод от другого клиента
    public void credit(BigDecimal amount) {
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
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
