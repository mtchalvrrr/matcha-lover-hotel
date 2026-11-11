package com.hotel.system.reservation;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A customer's request to extend an existing hotel stay.
 * Approval does not change the reservation dates until the full extension
 * charge has been confirmed by the payment provider.
 */
@Entity
@Table(name = "reservation_extension_requests", indexes = {
    @Index(name = "idx_extension_reservation_status", columnList = "reservation_id,status"),
    @Index(name = "idx_extension_customer", columnList = "customer_username")
})
public class ReservationExtensionRequest {
    public enum Status {
        PENDING,
        APPROVED_AWAITING_PAYMENT,
        PAID,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @Column(name = "customer_username", nullable = false, length = 80)
    private String customerUsername;

    @Column(name = "current_checkout_date", nullable = false)
    private LocalDate currentCheckoutDate;

    @Column(name = "requested_checkout_date", nullable = false)
    private LocalDate requestedCheckoutDate;

    @Column(name = "additional_nights", nullable = false)
    private int additionalNights;

    @Column(name = "nightly_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal nightlyRate;

    @Column(name = "extension_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal extensionAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.PENDING;

    @Column(length = 500)
    private String customerNote;

    @Column(length = 500)
    private String reviewNote;

    @Column(name = "reviewed_by", length = 80)
    private String reviewedBy;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Version
    @Column(nullable = false, columnDefinition = "integer default 0")
    private long version;

    protected ReservationExtensionRequest() {}

    public ReservationExtensionRequest(Long reservationId, String customerUsername,
            LocalDate currentCheckoutDate, LocalDate requestedCheckoutDate,
            int additionalNights, BigDecimal nightlyRate, BigDecimal extensionAmount,
            String customerNote) {
        this.reservationId = reservationId;
        this.customerUsername = customerUsername;
        this.currentCheckoutDate = currentCheckoutDate;
        this.requestedCheckoutDate = requestedCheckoutDate;
        this.additionalNights = additionalNights;
        this.nightlyRate = nightlyRate;
        this.extensionAmount = extensionAmount;
        this.customerNote = customerNote;
    }

    public Long getId() { return id; }
    public Long getReservationId() { return reservationId; }
    public String getCustomerUsername() { return customerUsername; }
    public LocalDate getCurrentCheckoutDate() { return currentCheckoutDate; }
    public LocalDate getRequestedCheckoutDate() { return requestedCheckoutDate; }
    public int getAdditionalNights() { return additionalNights; }
    public BigDecimal getNightlyRate() { return nightlyRate; }
    public BigDecimal getExtensionAmount() { return extensionAmount; }
    public Status getStatus() { return status; }
    public String getCustomerNote() { return customerNote; }
    public String getReviewNote() { return reviewNote; }
    public String getReviewedBy() { return reviewedBy; }
    public Long getPaymentId() { return paymentId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getPaidAt() { return paidAt; }

    public void approve(String admin, String note, Instant at) {
        requirePending();
        status = Status.APPROVED_AWAITING_PAYMENT;
        reviewedBy = admin;
        reviewNote = note;
        reviewedAt = at;
    }

    public void reject(String admin, String note, Instant at) {
        requirePending();
        status = Status.REJECTED;
        reviewedBy = admin;
        reviewNote = note;
        reviewedAt = at;
    }

    public void attachPayment(Long id) {
        if (status != Status.APPROVED_AWAITING_PAYMENT || id == null)
            throw new IllegalStateException("Only an approved extension can receive a payment.");
        if (paymentId != null && !paymentId.equals(id))
            throw new IllegalStateException("An extension payment is already linked.");
        paymentId = id;
    }

    public void replacePayment(Long newPaymentId) {
        if (status != Status.APPROVED_AWAITING_PAYMENT || newPaymentId == null)
            throw new IllegalStateException("Only an approved extension can replace a failed payment.");
        paymentId = newPaymentId;
    }

    public void markPaid(Instant at) {
        if (status == Status.PAID) return;
        if (status != Status.APPROVED_AWAITING_PAYMENT || paymentId == null)
            throw new IllegalStateException("This extension is not awaiting its approved payment.");
        status = Status.PAID;
        paidAt = at;
    }

    private void requirePending() {
        if (status != Status.PENDING)
            throw new IllegalStateException("This extension request has already been reviewed.");
    }
}