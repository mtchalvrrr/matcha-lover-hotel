package com.hotel.system.payment;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "processed_xendit_events", uniqueConstraints =
        @UniqueConstraint(name = "uk_xendit_event_key", columnNames = "event_key"))
public class ProcessedXenditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_key", nullable = false, unique = true, length = 180)
    private String eventKey;
    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt = Instant.now();

    protected ProcessedXenditEvent() {}
    public ProcessedXenditEvent(String eventKey, String eventType) {
        this.eventKey = eventKey;
        this.eventType = eventType;
    }
}
