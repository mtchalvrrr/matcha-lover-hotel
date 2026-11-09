package com.hotel.system.support;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "support_messages", indexes = {
        @Index(name = "idx_support_message_case_time", columnList = "support_case_id, created_at")
})
public class SupportMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "support_case_id", nullable = false)
    private Long supportCaseId;

    @Column(name = "sender_username", nullable = false, length = 80)
    private String senderUsername;

    @Column(name = "sender_role", nullable = false, length = 20)
    private String senderRole;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected SupportMessage() { }

    public SupportMessage(Long supportCaseId, String senderUsername, String senderRole, String message) {
        this.supportCaseId = supportCaseId;
        this.senderUsername = senderUsername;
        this.senderRole = senderRole;
        this.message = message;
    }

    public Long getId() { return id; }
    public Long getSupportCaseId() { return supportCaseId; }
    public String getSenderUsername() { return senderUsername; }
    public String getSenderRole() { return senderRole; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}
