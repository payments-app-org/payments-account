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

    private OffsetDateTime createdAt;

    protected User() {
    }

    public UUID getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getMiddleName() { return middleName; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public Subscription getSubscription() { return subscription; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
