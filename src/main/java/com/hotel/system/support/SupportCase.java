package com.hotel.system.support;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "support_cases", indexes = {
        @Index(name = "idx_support_customer_updated", columnList = "customer_username, updated_at"),
        @Index(name = "idx_support_status_updated", columnList = "status, updated_at")
})
public class SupportCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_username", nullable = false, length = 80)
    private String customerUsername;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, length = 120)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupportStatus status = SupportStatus.OPEN;

    @Column(nullable = false)
    private boolean visible = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected SupportCase() { }

    public SupportCase(String customerUsername, String category, String subject) {
        this.customerUsername = customerUsername;
        this.category = category;
        this.subject = subject;
    }

    public Long getId() { return id; }
    public String getCustomerUsername() { return customerUsername; }
    public String getCategory() { return category; }
    public String getSubject() { return subject; }
    public SupportStatus getStatus() { return status; }
    public boolean isVisible() { return visible; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void recordActivity() { this.updatedAt = Instant.now(); }
    public void setStatus(SupportStatus status) {
        this.status = status;
        recordActivity();
    }
    public void hide() {
        this.visible = false;
        recordActivity();
    }
}
