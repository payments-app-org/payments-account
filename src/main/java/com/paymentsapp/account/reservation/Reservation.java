package com.paymentsapp.account.reservation;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    private UUID id;

    private UUID paymentId;
    private UUID userId;
    private UUID accountId;
    // Карта нашего клиента-получателя; null — перевод во внешний банк
    private UUID recipientAccountId;
    private String operationType;
    private BigDecimal amount;
    private BigDecimal commission;
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    protected Reservation() {
    }

    public Reservation(UUID paymentId, UUID userId, UUID accountId, UUID recipientAccountId, String operationType,
                       BigDecimal amount, BigDecimal commission) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.userId = userId;
        this.accountId = accountId;
        this.recipientAccountId = recipientAccountId;
        this.operationType = operationType;
        this.amount = amount;
        this.commission = commission;
        this.total = amount.add(commission);
        this.status = ReservationStatus.RESERVED;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = createdAt;
    }

    // Тот же платёж пришёл повторно — параметры должны совпасть
    public boolean sameRequest(UUID userId, UUID accountId, UUID recipientAccountId, BigDecimal amount,
                               BigDecimal commission) {
        return this.userId.equals(userId) && this.accountId.equals(accountId)
                && java.util.Objects.equals(this.recipientAccountId, recipientAccountId)
                && this.amount.compareTo(amount) == 0 && this.commission.compareTo(commission) == 0;
    }

    void changeStatus(ReservationStatus status) {
        this.status = status;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getUserId() { return userId; }
    public UUID getAccountId() { return accountId; }
    public UUID getRecipientAccountId() { return recipientAccountId; }
    public String getOperationType() { return operationType; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getCommission() { return commission; }
    public BigDecimal getTotal() { return total; }
    public ReservationStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
