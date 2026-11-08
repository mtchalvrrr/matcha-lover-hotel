package com.hotel.system.reservation;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "reservation_extension_requests", indexes = {
    @Index(name = "idx_extension_status_created", columnList = "status,created_at"),
    @Index(name = "idx_extension_reservation", columnList = "reservation_id,status")
})
public class ReservationExtensionRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;
    @Column(name = "customer_username", nullable = false, length = 80)
    private String customerUsername;
    @Column(name = "original_check_out", nullable = false)
    private LocalDate originalCheckOut;
    @Column(name = "requested_check_out", nullable = false)
    private LocalDate requestedCheckOut;
    @Column(name = "extra_nights", nullable = false)
    private int extraNights;
    @Column(name = "additional_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal additionalAmount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ExtensionRequestStatus status = ExtensionRequestStatus.PENDING;
    @Column(length = 1000)
    private String reason;
    @Column(name = "reviewed_by", length = 80)
    private String reviewedBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected ReservationExtensionRequest() {}

    public ReservationExtensionRequest(Long reservationId, String customerUsername,
            LocalDate originalCheckOut, LocalDate requestedCheckOut,
            int extraNights, BigDecimal additionalAmount) {
        this.reservationId = reservationId;
        this.customerUsername = customerUsername;
        this.originalCheckOut = originalCheckOut;
        this.requestedCheckOut = requestedCheckOut;
        this.extraNights = extraNights;
        this.additionalAmount = additionalAmount;
    }

    public void review(boolean approve, String reviewer, String reason, Instant at) {
        if (status != ExtensionRequestStatus.PENDING) {
            throw new IllegalStateException("This extension request has already been reviewed.");
        }
        if (!approve && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("A reason is required when rejecting an extension request.");
        }
        status = approve ? ExtensionRequestStatus.APPROVED : ExtensionRequestStatus.REJECTED;
        reviewedBy = reviewer;
        this.reason = reason == null ? "" : reason.trim();
        reviewedAt = at;
    }

    public Long getId() { return id; }
    public Long getReservationId() { return reservationId; }
    public String getCustomerUsername() { return customerUsername; }
    public LocalDate getOriginalCheckOut() { return originalCheckOut; }
    public LocalDate getRequestedCheckOut() { return requestedCheckOut; }
    public int getExtraNights() { return extraNights; }
    public BigDecimal getAdditionalAmount() { return additionalAmount; }
    public ExtensionRequestStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public String getReviewedBy() { return reviewedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReviewedAt() { return reviewedAt; }
}
