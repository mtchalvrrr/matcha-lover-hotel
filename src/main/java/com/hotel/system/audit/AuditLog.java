package com.hotel.system.audit;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_created_at", columnList = "created_at"),
    @Index(name = "idx_audit_actor", columnList = "actor_username")
})
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "actor_username", nullable = false, length = 80)
    private String actorUsername;
    @Column(name = "event_action", nullable = false, length = 80)
    private String action;
    @Column(name = "target_type", nullable = false, length = 60)
    private String targetType;
    @Column(name = "target_id", length = 80)
    private String targetId;
    @Column(length = 1000)
    private String details;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AuditLog() {}
    public AuditLog(String actorUsername, String action, String targetType, String targetId, String details) {
        this.actorUsername = actorUsername;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.details = details;
    }
    public Long getId() { return id; }
    public String getActorUsername() { return actorUsername; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}