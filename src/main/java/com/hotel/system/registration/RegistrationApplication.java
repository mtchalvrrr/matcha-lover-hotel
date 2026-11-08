package com.hotel.system.registration;

import jakarta.persistence.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Instant;

@Entity
@Table(name = "customer_registration_applications", indexes = {
    @Index(name = "idx_registration_status", columnList = "status"),
    @Index(name = "idx_registration_email", columnList = "email")
})
public class RegistrationApplication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;
    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;
    @Column(nullable = false, length = 254)
    private String email;
    @Column(nullable = false, length = 80)
    private String username;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Column(name = "id_document_path", nullable = false, length = 500)
    private String idDocumentPath;
    @Column(name = "document_hash", length = 64)
    private String documentHash;
    @Column(name = "otp_hash", nullable = false, length = 100)
    private String otpHash;
    @Column(name = "otp_expires_at", nullable = false)
    private Instant otpExpiresAt;
    @Column(name = "otp_attempts", nullable = false)
    private int otpAttempts;
    @Column(name = "otp_sent_at")
    private Instant otpSentAt = Instant.now();
    @Column(name = "otp_resends", nullable = false, columnDefinition = "integer default 0")
    private int otpResends;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RegistrationStatus status = RegistrationStatus.PENDING_OTP;
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt = Instant.now();
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "reviewed_by", length = 80)
    private String reviewedBy;
    @Column(name = "review_reason", length = 1000)
    private String reviewReason;

    protected RegistrationApplication() {}
    public RegistrationApplication(String firstName, String lastName, String email, String username,
        String passwordHash, String idDocumentPath, String documentHash, String otpHash, Instant otpExpiresAt) {
        this.firstName = firstName; this.lastName = lastName; this.email = email;
        this.username = username; this.passwordHash = passwordHash; this.idDocumentPath = idDocumentPath;
        this.documentHash = documentHash;
        this.otpHash = otpHash; this.otpExpiresAt = otpExpiresAt;
        this.otpSentAt = otpExpiresAt.minusSeconds(600);
    }
    public Long getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getIdDocumentPath() { return idDocumentPath; }
    public RegistrationStatus getStatus() { return status; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewedBy() { return reviewedBy; }
    public String getReviewReason() { return reviewReason; }

    public void resendOtp(String newOtpHash, Instant now) {
        if (status != RegistrationStatus.PENDING_OTP) {
            throw new IllegalStateException("This registration no longer needs email verification.");
        }
        if (otpSentAt != null && otpSentAt.plusSeconds(60).isAfter(now)) {
            throw new IllegalStateException("Wait at least 60 seconds before requesting another code.");
        }
        if (otpResends >= 3) {
            throw new IllegalStateException("The resend limit has been reached. Submit a new registration application.");
        }
        otpHash = newOtpHash;
        otpExpiresAt = now.plusSeconds(600);
        otpSentAt = now;
        otpAttempts = 0;
        otpResends++;
    }

    public boolean verifyOtp(String candidate, PasswordEncoder encoder, Instant now) {
        if (status != RegistrationStatus.PENDING_OTP) throw new IllegalStateException("This OTP is no longer active.");
        if (!otpExpiresAt.isAfter(now)) throw new IllegalStateException("OTP expired. Submit a new registration request.");
        if (otpAttempts >= 5) throw new IllegalStateException("OTP attempt limit reached.");
        otpAttempts++;
        if (!encoder.matches(candidate, otpHash)) return false;
        status = RegistrationStatus.PENDING_REVIEW;
        otpHash = "VERIFIED";
        return true;
    }
    public void review(boolean approve, String reviewer, String reason) {
        if (status != RegistrationStatus.PENDING_REVIEW) throw new IllegalStateException("Only verified applications can be reviewed.");
        status = approve ? RegistrationStatus.APPROVED : RegistrationStatus.REJECTED;
        reviewedBy = reviewer;
        reviewedAt = Instant.now();
        reviewReason = reason;
    }
}