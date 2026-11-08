package com.hotel.system.payment;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "processed_paymongo_events", uniqueConstraints =
        @UniqueConstraint(name = "uk_paymongo_event_id", columnNames = "event_id"))
public class ProcessedPayMongoEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;
    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt = Instant.now();

    protected ProcessedPayMongoEvent() { }
    public ProcessedPayMongoEvent(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
    }
}
