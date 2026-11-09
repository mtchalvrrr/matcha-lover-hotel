package com.hotel.system.payment;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Stores processed NexusPay event IDs so webhook retries cannot apply a
 * payment or refund transition more than once.
 */
@Entity
@Table(name = "processed_payment_events", uniqueConstraints =
        @UniqueConstraint(name = "uk_payment_event_key", columnNames = "event_key"))
public class ProcessedPaymentEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_key", nullable = false, unique = true, length = 180)
    private String eventKey;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt = Instant.now();

    protected ProcessedPaymentEvent() {}

    public ProcessedPaymentEvent(String eventKey, String eventType) {
        this.eventKey = eventKey;
        this.eventType = eventType;
    }
}
