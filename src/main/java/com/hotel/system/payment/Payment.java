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
    @Column(name = "provider_checkout_session_id", unique = true, length = 100)
    private String providerCheckoutSessionId;
    @Column(name = "checkout_url", length = 1000)
    private String checkoutUrl;
    @Column(name = "provider_payment_id", length = 100)
    private String providerPaymentId;
    @Column(name = "provider_refund_id", length = 100)
    private String providerRefundId;
    @Column(name = "provider_refund_status", length = 24)
    private String providerRefundStatus;

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
    public String getProviderCheckoutSessionId() { return providerCheckoutSessionId; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public String getProviderRefundId() { return providerRefundId; }
    public String getProviderRefundStatus() { return providerRefundStatus; }

    public void attachCheckoutSession(String sessionId, String url) {
        if (status != PaymentStatus.PENDING || sessionId == null || sessionId.isBlank()
                || url == null || !url.startsWith("https://")) {
            throw new IllegalStateException("A valid pending checkout session is required.");
        }
        this.providerCheckoutSessionId = sessionId;
        this.checkoutUrl = url;
    }

    public void markPaidFromProvider(String sessionId, String providerPaymentId) {
        if (status == PaymentStatus.PAID) return;
        if (status != PaymentStatus.PENDING
                || providerCheckoutSessionId == null
                || !providerCheckoutSessionId.equals(sessionId)) {
            throw new IllegalStateException("The payment does not match an active provider checkout.");
        }
        status = PaymentStatus.PAID;
        this.providerPaymentId = providerPaymentId;
    }

    public void markFailedFromProvider(String providerPaymentId) {
        if (status != PaymentStatus.PENDING) return;
        status = PaymentStatus.FAILED;
        if (providerPaymentId != null && !providerPaymentId.isBlank())
            this.providerPaymentId = providerPaymentId;
    }

    public void markRefundPending(String refundId, String providerStatus) {
        if (status != PaymentStatus.PAID && status != PaymentStatus.REFUND_PENDING)
            throw new IllegalStateException("Only paid transactions can enter refund processing.");
        this.providerRefundId = refundId;
        this.providerRefundStatus = providerStatus;
        this.status = PaymentStatus.REFUND_PENDING;
    }

    public void markRefunded(Instant refundedAt) {
        if (status != PaymentStatus.PAID && status != PaymentStatus.REFUND_PENDING) {
            throw new IllegalStateException("Only paid or refund-pending transactions can be refunded.");
        }
        if (refundedAt == null) throw new IllegalArgumentException("A refund timestamp is required.");
        status = PaymentStatus.REFUNDED;
        this.providerRefundStatus = "succeeded";
        this.refundedAt = refundedAt;
    }

    public void markRefundFailed(String refundId) {
        if (refundId != null) this.providerRefundId = refundId;
        this.providerRefundStatus = "failed";
        if (status == PaymentStatus.REFUND_PENDING) status = PaymentStatus.PAID;
    }

    public void markVoided() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only pending transactions can be voided.");
        }
        status = PaymentStatus.VOIDED;
    }
}