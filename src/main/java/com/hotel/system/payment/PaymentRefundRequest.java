package com.hotel.system.payment;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "payment_refund_requests", indexes = {
        @Index(name = "idx_refund_request_status", columnList = "status"),
        @Index(name = "idx_refund_request_payment", columnList = "payment_id")
})
public class PaymentRefundRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "payment_id", nullable = false)
    private Long paymentId;
    @Column(name = "requested_by", nullable = false, length = 80)
    private String requestedBy;
    @Column(nullable = false, length = 500)
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RefundRequestStatus status = RefundRequestStatus.PENDING;
    @Column(name = "reviewed_by", length = 80)
    private String reviewedBy;
    @Column(name = "review_reason", length = 500)
    private String reviewReason;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Version
    @Column(name = "version", nullable = false, columnDefinition = "integer default 0")
    private long version;

    protected PaymentRefundRequest() {}

    public PaymentRefundRequest(Long paymentId, String requestedBy, String reason) {
        this.paymentId = paymentId;
        this.requestedBy = requestedBy;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public Long getPaymentId() { return paymentId; }
    public String getRequestedBy() { return requestedBy; }
    public String getReason() { return reason; }
    public RefundRequestStatus getStatus() { return status; }
    public String getReviewedBy() { return reviewedBy; }
    public String getReviewReason() { return reviewReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReviewedAt() { return reviewedAt; }

    public void review(boolean approve, String reviewer, String reviewReason, Instant now) {
        if (status != RefundRequestStatus.PENDING) {
            throw new IllegalStateException("This refund request has already been reviewed.");
        }
        if (reviewer == null || reviewer.isBlank()
                || reviewer.equalsIgnoreCase(requestedBy)) {
            throw new IllegalArgumentException("A different administrator must authorize this refund.");
        }
        if (!approve && (reviewReason == null || reviewReason.isBlank())) {
            throw new IllegalArgumentException("A reason is required when rejecting a refund request.");
        }
        if (reviewReason != null && reviewReason.length() > 500) {
            throw new IllegalArgumentException("The review reason must be 500 characters or fewer.");
        }
        this.status = approve ? RefundRequestStatus.APPROVED : RefundRequestStatus.REJECTED;
        this.reviewedBy = reviewer;
        this.reviewReason = reviewReason == null ? "" : reviewReason.trim();
        this.reviewedAt = now;
    }
}
