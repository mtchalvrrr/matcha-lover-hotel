package com.hotel.system.reservation;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "reservation_extension_requests", indexes = {
    @Index(name = "idx_extension_reservation", columnList = "reservation_id"),
    @Index(name = "idx_extension_status", columnList = "status")
})
public class ReservationExtensionRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @Column(name = "customer_username", nullable = false, length = 80)
    private String customerUsername;

    @Column(name = "previous_checkout_date", nullable = false)
    private LocalDate previousCheckoutDate;

    @Column(name = "requested_checkout_date", nullable = false)
    private LocalDate requestedCheckoutDate;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExtensionRequestStatus status = ExtensionRequestStatus.PENDING;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "reviewed_by", length = 80)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Version
    @Column(nullable = false, columnDefinition = "integer default 0")
    private long version;

    protected ReservationExtensionRequest() { }

    public ReservationExtensionRequest(Long reservationId, String customerUsername,
            LocalDate previousCheckoutDate, LocalDate requestedCheckoutDate, String reason) {
        this.reservationId = reservationId;
        this.customerUsername = customerUsername;
        this.previousCheckoutDate = previousCheckoutDate;
        this.requestedCheckoutDate = requestedCheckoutDate;
        this.reason = reason;
    }

    public void review(boolean approve, String reviewer, String reason, Instant at) {
        if (status != ExtensionRequestStatus.PENDING)
            throw new IllegalStateException("This extension request has already been reviewed.");
        status = approve ? ExtensionRequestStatus.APPROVED : ExtensionRequestStatus.REJECTED;
        reviewedBy = reviewer;
        reviewedAt = at;
        reviewReason = reason;
    }

    public Long getId() { return id; }
    public Long getReservationId() { return reservationId; }
    public String getCustomerUsername() { return customerUsername; }
    public LocalDate getPreviousCheckoutDate() { return previousCheckoutDate; }
    public LocalDate getRequestedCheckoutDate() { return requestedCheckoutDate; }
    public String getReason() { return reason; }
    public ExtensionRequestStatus getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
    public String getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewReason() { return reviewReason; }
}
