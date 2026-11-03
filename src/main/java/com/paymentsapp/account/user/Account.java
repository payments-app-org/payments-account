package com.paymentsapp.account.user;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// Счёт пользователя: «Мой кошелёк» есть у всех, карты — у тех, кто их оформил
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;

    private UUID userId;

    @Enumerated(EnumType.STRING)
    private AccountType type;

    private String title;
    private BigDecimal balance;
    private String currency;
    private String cardLast4;
    private String paymentSystem;
    private OffsetDateTime createdAt;

    protected Account() {
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public AccountType getType() { return type; }
    public String getTitle() { return title; }
    public BigDecimal getBalance() { return balance; }
    public String getCurrency() { return currency; }
    public String getCardLast4() { return cardLast4; }
    public String getPaymentSystem() { return paymentSystem; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
