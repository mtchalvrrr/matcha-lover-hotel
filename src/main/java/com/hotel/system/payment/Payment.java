package com.hotel.system.payment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "reservation_payments", indexes = {
    @Index(name = "idx_payment_reservation", columnList = "reservation_id"),
    @Index(name = "idx_payment_status", columnList = "status")
})
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;
    @Column(name = "customer_username", nullable = false, length = 80)
    private String customerUsername;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 24)
    private PaymentType paymentType;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    @Column(name = "payment_method", nullable = false, length = 40)
    private String paymentMethod;
    @Column(name = "transaction_reference", nullable = false, unique = true, length = 100)
    private String transactionReference;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "refunded_at")
    private Instant refundedAt;
    @Version
    @Column(name = "version", nullable = false, columnDefinition = "integer default 0")
    private long version;

    protected Payment() {}
    public Payment(Long reservationId, String customerUsername, PaymentType paymentType,
                   BigDecimal amount, String paymentMethod, String transactionReference,
                   PaymentStatus status) {
        this.reservationId = reservationId;
        this.customerUsername = customerUsername;
        this.paymentType = paymentType;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.transactionReference = transactionReference;
        this.status = status;
    }
    public Long getId() { return id; }
    public Long getReservationId() { return reservationId; }
    public String getCustomerUsername() { return customerUsername; }
    public PaymentType getPaymentType() { return paymentType; }
    public BigDecimal getAmount() { return amount; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getTransactionReference() { return transactionReference; }
    public PaymentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRefundedAt() { return refundedAt; }

    public void markRefunded(Instant refundedAt) {
        if (status != PaymentStatus.PAID) {
            throw new IllegalStateException("Only paid transactions can be refunded.");
        }
        if (refundedAt == null) {
            throw new IllegalArgumentException("A refund timestamp is required.");
        }
        status = PaymentStatus.REFUNDED;
        this.refundedAt = refundedAt;
    }

    public void markVoided() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only pending transactions can be voided.");
        }
        status = PaymentStatus.VOIDED;
    }
}