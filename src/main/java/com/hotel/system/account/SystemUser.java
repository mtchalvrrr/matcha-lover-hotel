package com.hotel.system.account;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "system_users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_system_users_username", columnNames = "username"),
        @UniqueConstraint(name = "uk_system_users_email", columnNames = "email")
})
public class SystemUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", length = 80)
    private String firstName;

    @Column(name = "last_name", length = 80)
    private String lastName;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountRole role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected SystemUser() {
    }

    public SystemUser(String username, String email, String passwordHash, AccountRole role) {
        this(null, null, username, email, passwordHash, role);
    }

    public SystemUser(String firstName, String lastName, String username, String email, String passwordHash, AccountRole role) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = true;
    }

    public Long getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public AccountRole getRole() { return role; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }

    public void setActive(boolean active) { this.active = active; }
    public void updateProfile(String firstName, String lastName, String email) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
