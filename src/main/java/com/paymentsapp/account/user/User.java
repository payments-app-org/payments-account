package com.paymentsapp.account.user;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    private String firstName;
    private String lastName;
    private String middleName;
    private String phone;
    private String email;

    @Enumerated(EnumType.STRING)
    private Subscription subscription;

    private String sbpDefaultBankId;
    private UUID defaultPaymentAccountId;
    @Enumerated(EnumType.STRING)
    private IdentificationLevel identificationLevel;

    private OffsetDateTime createdAt;

    // «Округление на пользу»: шаг (10, 100, 1000 ₽) и благотворительный фонд; null — выключено
    private Integer roundupStep;
    private String roundupFundCode;
    private String roundupFundName;
    private OffsetDateTime roundupEnabledAt;

    protected User() {
    }

    // Как получатель видит отправителя в СБП: «Артур Константинович Р»
    public String displayName() {
        String initial = lastName.isEmpty() ? "" : lastName.substring(0, 1);
        return middleName == null ? firstName + " " + initial : firstName + " " + middleName + " " + initial;
    }

    public UUID getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getMiddleName() { return middleName; }
    public Integer getRoundupStep() { return roundupStep; }
    public String getRoundupFundCode() { return roundupFundCode; }
    public String getRoundupFundName() { return roundupFundName; }
    public OffsetDateTime getRoundupEnabledAt() { return roundupEnabledAt; }

    public void enableRoundup(int step, String fundCode, String fundName) {
        this.roundupStep = step;
        this.roundupFundCode = fundCode;
        this.roundupFundName = fundName;
        this.roundupEnabledAt = OffsetDateTime.now();
    }

    public void disableRoundup() {
        this.roundupStep = null;
        this.roundupFundCode = null;
        this.roundupFundName = null;
        this.roundupEnabledAt = null;
    }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public Subscription getSubscription() { return subscription; }
    public String getSbpDefaultBankId() { return sbpDefaultBankId; }
    public UUID getDefaultPaymentAccountId() { return defaultPaymentAccountId; }
    public IdentificationLevel getIdentificationLevel() { return identificationLevel; }

    public void setDefaultPaymentAccount(UUID accountId) {
        this.defaultPaymentAccountId = accountId;
    }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
