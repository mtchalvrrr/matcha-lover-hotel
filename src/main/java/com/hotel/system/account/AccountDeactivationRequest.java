package com.hotel.system.account;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "account_deactivation_requests", indexes = {
        @Index(name = "idx_deactivation_status", columnList = "status"),
        @Index(name = "idx_deactivation_user", columnList = "user_id")
})
public class AccountDeactivationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeactivationRequestStatus status = DeactivationRequestStatus.PENDING;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by", length = 80)
    private String reviewedBy;

    @Column(name = "review_reason", length = 1000)
    private String reviewReason;

    protected AccountDeactivationRequest() { }

    public AccountDeactivationRequest(Long userId, String username, String reason) {
        this.userId = userId;
        this.username = username;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getReason() { return reason; }
    public DeactivationRequestStatus getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewedBy() { return reviewedBy; }
    public String getReviewReason() { return reviewReason; }

    public void review(boolean approve, String reviewer, String reason) {
        if (status != DeactivationRequestStatus.PENDING) {
            throw new IllegalStateException("This deactivation request has already been reviewed.");
        }
        if (!approve && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("A reason is required when rejecting a deactivation request.");
        }
        status = approve ? DeactivationRequestStatus.APPROVED : DeactivationRequestStatus.REJECTED;
        reviewedBy = reviewer;
        reviewedAt = Instant.now();
        reviewReason = reason == null ? "" : reason.trim();
    }
}
