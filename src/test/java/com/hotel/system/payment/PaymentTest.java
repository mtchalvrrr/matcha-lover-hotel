package com.hotel.system.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PaymentTest {
    private Payment paidPayment() {
        return new Payment(10L, "customer", PaymentType.DOWN_PAYMENT,
                new BigDecimal("500.00"), "SIMULATED", "SIM-REFERENCE", PaymentStatus.PAID);
    }

    @Test
    void paidPaymentCanBeRefundedOnce() {
        Payment payment = paidPayment();

        Instant refundedAt = Instant.parse("2026-11-08T04:00:00Z");
        payment.markRefunded(refundedAt);

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        assertEquals(refundedAt, payment.getRefundedAt());
        assertThrows(IllegalStateException.class, () -> payment.markRefunded(refundedAt));
    }

    @Test
    void onlyPendingPaymentCanBeVoided() {
        Payment pending = new Payment(10L, "customer", PaymentType.BALANCE,
                new BigDecimal("500.00"), "GATEWAY", "PENDING-REFERENCE", PaymentStatus.PENDING);

        pending.markVoided();

        assertEquals(PaymentStatus.VOIDED, pending.getStatus());
        assertThrows(IllegalStateException.class, pending::markVoided);
        assertThrows(IllegalStateException.class, paidPayment()::markVoided);
    }
}
