package com.hotel.system.registration;

import com.hotel.system.account.*;
import com.hotel.system.audit.AuditService;
import com.hotel.system.notification.NotificationService;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.IOException;
import java.util.HexFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class RegistrationService {
    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);
    private static final List<RegistrationStatus> ACTIVE_APPLICATIONS =
        List.of(RegistrationStatus.PENDING_OTP, RegistrationStatus.PENDING_REVIEW);
    private static final List<RegistrationStatus> DOCUMENT_HASH_BLOCKING_STATUSES =
        List.of(RegistrationStatus.PENDING_OTP, RegistrationStatus.PENDING_REVIEW, RegistrationStatus.APPROVED);
    private final RegistrationRepository applications;
    private final SystemUserRepository users;
    private final RegistrationDocumentStorage documents;
    private final PasswordEncoder encoder;
    private final JavaMailSender mail;
    private final Clock clock;
    private final AuditService audit;
    private final String fromAddress;
    private final NotificationService notifications;
    private final SecureRandom random = new SecureRandom();

    public RegistrationService(RegistrationRepository applications, SystemUserRepository users,
        RegistrationDocumentStorage documents, PasswordEncoder encoder, JavaMailSender mail,
        Clock clock, AuditService audit, @Value("${spring.mail.username:}") String fromAddress, NotificationService notifications) {
        this.applications = applications; this.users = users; this.documents = documents;
        this.encoder = encoder; this.mail = mail; this.clock = clock; this.audit = audit;
        this.fromAddress = fromAddress;
        this.notifications = notifications;
    }

    public RegistrationApplication submit(String firstName, String lastName, String email,
        String username, String password, org.springframework.web.multipart.MultipartFile idFile) {
        String first = clean(firstName), last = clean(lastName);
        String address = clean(email).toLowerCase(), user = clean(username);
        if (first.isBlank() || last.isBlank() || first.length() > 80 || last.length() > 80)
            throw new IllegalArgumentException("Enter a valid first and last name.");
        if (!address.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$") || address.length() > 254)
            throw new IllegalArgumentException("Enter a valid email address.");
        if (user.length() < 4 || user.length() > 80) throw new IllegalArgumentException("Username must be 4 to 80 characters.");
        validatePassword(password);
        if (users.existsByEmailIgnoreCase(address) || applications.existsByEmailIgnoreCaseAndStatusIn(address, ACTIVE_APPLICATIONS))
            throw new IllegalArgumentException("An account or active application already uses this email.");
        if (users.existsByUsernameIgnoreCase(user) || applications.existsByUsernameIgnoreCaseAndStatusIn(user, ACTIVE_APPLICATIONS))
            throw new IllegalArgumentException("An account or active application already uses this username.");

        String documentHash = null;
        String path = "";
        if (idFile != null && !idFile.isEmpty()) {
            documentHash = fingerprint(idFile);
            if (applications.existsByDocumentHashAndStatusIn(documentHash, DOCUMENT_HASH_BLOCKING_STATUSES)) {
                throw new IllegalArgumentException("This ID document has already been submitted with another application.");
            }
            path = documents.store(idFile);
        }
        String otp = String.format("%06d", random.nextInt(1_000_000));
        RegistrationApplication application;
        try {
            application = applications.save(new RegistrationApplication(first, last,
                    address, user, encoder.encode(password), path, documentHash, encoder.encode(otp),
                    Instant.now(clock).plus(Duration.ofMinutes(5))));
        } catch (RuntimeException ex) {
            if (!path.isBlank()) {
                try { documents.delete(path); } catch (RuntimeException cleanup) { ex.addSuppressed(cleanup); }
            }
            throw ex;
        }
        try {
            if (fromAddress == null || fromAddress.isBlank()) throw new IllegalStateException("Email delivery is not configured.");
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(address);
            message.setSubject("[HOTEL NAME] — Email verification code");
            message.setText("Your email verification code is " + otp + ". It expires in 5 minutes. If you did not request this, ignore this email.");
            mail.send(message);
        } catch (RuntimeException ex) {
            applications.delete(application);
            if (!path.isBlank()) {
                try { documents.delete(path); } catch (RuntimeException cleanup) { ex.addSuppressed(cleanup); }
            }
            throw new IllegalStateException("We could not send the verification code. Email delivery must be configured before registration.", ex);
        }
        audit.record(user, "CUSTOMER_REGISTRATION_SUBMITTED", "REGISTRATION", application.getId().toString(),
            "Application submitted; email OTP sent.");
        return application;
    }


    public void resendOtp(Long applicationId, String email) {
        if (applicationId == null || email == null || email.isBlank() || email.length() > 254) {
            throw new IllegalArgumentException("Registration application not found.");
        }
        RegistrationApplication app = applications.findByIdAndEmailIgnoreCase(
                        applicationId, email.trim().toLowerCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("Registration application not found."));
        String otp = String.format("%06d", random.nextInt(1_000_000));
        app.resendOtp(encoder.encode(otp), Instant.now(clock));
        try {
            if (fromAddress == null || fromAddress.isBlank()) {
                throw new IllegalStateException("Email delivery is not configured.");
            }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(app.getEmail());
            message.setSubject("[HOTEL NAME] — Email verification code");
            message.setText("Your new email verification code is " + otp
                    + ". It expires in 5 minutes. If you did not request this, ignore this email.");
            mail.send(message);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("We could not send a new verification code. Please try again later.", ex);
        }
        audit.record(app.getUsername(), "REGISTRATION_OTP_RESENT", "REGISTRATION", applicationId.toString(),
                "A new email verification code was sent.");
    }

    public boolean verify(Long id, String email, String otp) {
        if (id == null || email == null || email.isBlank() || email.length() > 254) {
            throw new IllegalArgumentException("Registration application not found.");
        }
        RegistrationApplication app = applications.findByIdAndEmailIgnoreCase(
                        id, email.trim().toLowerCase(java.util.Locale.ROOT))
            .orElseThrow(() -> new IllegalArgumentException("Registration application not found."));
        if (otp == null) return false;
        boolean verified = app.verifyOtp(otp.trim(), encoder, Instant.now(clock));
        if (verified) {
            audit.record(app.getUsername(), "REGISTRATION_EMAIL_VERIFIED", "REGISTRATION", id.toString(),
                "Email OTP verified. Identity documents are still required before review.");
        }
        return verified;
    }



    public void attachIdentityDocuments(Long id, String email, String idType,
            org.springframework.web.multipart.MultipartFile front, org.springframework.web.multipart.MultipartFile back) {
        RegistrationApplication app = applications.findByIdAndEmailIgnoreCase(id,
                clean(email).toLowerCase(java.util.Locale.ROOT))
            .orElseThrow(() -> new IllegalArgumentException("Registration application not found."));
        String type = clean(idType);
        if (type.isBlank() || type.length() > 80)
            throw new IllegalArgumentException("Select your government-issued ID type.");
        if (!app.isEmailVerified())
            throw new IllegalStateException("Verify your email address before submitting identity documents.");
        if (front == null || front.isEmpty() || back == null || back.isEmpty())
            throw new IllegalArgumentException("Upload both the front and back of your government-issued ID.");
        String frontHash = fingerprint(front), backHash = fingerprint(back);
        String combinedHash;
        try {
            combinedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((frontHash + ":" + backHash).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Secure document fingerprinting is unavailable.", ex);
        }
        if (applications.existsByDocumentHashAndStatusIn(combinedHash, DOCUMENT_HASH_BLOCKING_STATUSES))
            throw new IllegalArgumentException("These ID documents have already been submitted.");
        String frontPath = "", backPath = "";
        try {
            frontPath = documents.store(front);
            backPath = documents.store(back);
            if (users.existsByUsernameIgnoreCase(app.getUsername()) || users.existsByEmailIgnoreCase(app.getEmail()))
                throw new IllegalStateException("A matching account already exists. Please contact the hotel.");
            app.attachIdentityDocuments(type, frontPath, backPath, combinedHash);
            notifications.notifySuperAdmins("Customer registration pending review",
                    "A customer registration application (#" + id + ") has verified its email and submitted both sides of a government-issued ID. Review the application before activating the account.",
                    "REGISTRATION");
            audit.record(app.getUsername(), "CUSTOMER_REGISTRATION_IDENTITY_SUBMITTED", "REGISTRATION",
                id.toString(), "Email verified and both ID images submitted; awaiting Super Admin review.");
        } catch (RuntimeException ex) {
            if (!frontPath.isBlank()) try { documents.delete(frontPath); } catch (RuntimeException cleanup) { ex.addSuppressed(cleanup); }
            if (!backPath.isBlank()) try { documents.delete(backPath); } catch (RuntimeException cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        }
    }

    public List<RegistrationApplication> pendingReview() {
        return applications.findByStatusOrderBySubmittedAtAsc(RegistrationStatus.PENDING_REVIEW);
    }

    @Transactional(readOnly = true)
    public List<RegistrationApplication> approvedApplications() {
        return applications.findByStatusOrderBySubmittedAtAsc(RegistrationStatus.APPROVED);
    }

    public void review(Long id, boolean approve, String reviewer, String reason) {
        RegistrationApplication app = applications.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Registration application not found."));
        String reviewReason = clean(reason);
        if (!approve && reviewReason.isBlank())
            throw new IllegalArgumentException("A reason is required when rejecting an application.");
        if (reviewReason.length() > 1000)
            throw new IllegalArgumentException("The review reason must be 1,000 characters or fewer.");
        app.review(approve, reviewer, reviewReason);
        if (approve) {
            if (users.existsByUsernameIgnoreCase(app.getUsername()) || users.existsByEmailIgnoreCase(app.getEmail()))
                throw new IllegalStateException("A matching account already exists; resolve the duplicate before approval.");
            users.save(new SystemUser(app.getFirstName(), app.getLastName(), app.getUsername(), app.getEmail(), app.getPasswordHash(), AccountRole.CUSTOMER));
            notifications.notifyUser(app.getUsername(), "Customer account approved",
                "Your customer account has been approved. You can now sign in.", "ACCOUNT");
        } else if (fromAddress != null && !fromAddress.isBlank()) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromAddress);
                message.setTo(app.getEmail());
                message.setSubject("[HOTEL NAME] — Registration application update");
                message.setText("Your customer registration application was not approved. Reason: "
                        + reviewReason + ". You may contact the hotel if you need clarification.");
                mail.send(message);
            } catch (RuntimeException ex) {
                log.warn("Registration rejection email could not be delivered for application {}", id);
            }
        }
        audit.record(reviewer, approve ? "CUSTOMER_REGISTRATION_APPROVED" : "CUSTOMER_REGISTRATION_REJECTED",
            "REGISTRATION", id.toString(), "Applicant " + app.getUsername() + ". " + reviewReason);

    }

    public RegistrationApplication findStatus(Long applicationId, String email) {
        if (applicationId == null || email == null || email.isBlank() || email.length() > 254) {
            throw new IllegalArgumentException("No matching registration application was found.");
        }
        return applications.findByIdAndEmailIgnoreCase(applicationId, email.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("No matching registration application was found."));
    }

    public RegistrationApplication get(Long id) {
        return applications.findById(id).orElseThrow(() -> new IllegalArgumentException("Registration application not found."));
    }

    public java.nio.file.Path documentPath(Long id) {
        String storedPath = get(id).getIdDocumentPath();
        if (storedPath == null || storedPath.isBlank()) {
            throw new IllegalArgumentException("No ID document was submitted with this application.");
        }
        return documents.resolve(storedPath);
    }

    public java.nio.file.Path documentPath(Long id, String side) {
        RegistrationApplication app = get(id);
        String storedPath = "back".equalsIgnoreCase(side) ? app.getIdDocumentBackPath() : app.getIdDocumentPath();
        if (storedPath == null || storedPath.isBlank()) throw new IllegalArgumentException("Requested ID image was not submitted.");
        return documents.resolve(storedPath);
    }

    public java.nio.file.Path approvedDocumentPath(Long id) {
        RegistrationApplication application = get(id);
        if (application.getStatus() != RegistrationStatus.APPROVED)
            throw new IllegalArgumentException("Approved customer document not found.");
        if (application.getIdDocumentPath() == null || application.getIdDocumentPath().isBlank())
            throw new IllegalArgumentException("No ID document was submitted with this application.");
        return documents.resolve(application.getIdDocumentPath());
    }

    private String fingerprint(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload a valid ID document.");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(file.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (IOException ex) {
            throw new IllegalArgumentException("The ID document could not be read. Please upload it again.", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Secure document fingerprinting is unavailable.", ex);
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 12 || !password.matches(".*[A-Z].*")
            || !password.matches(".*[a-z].*") || !password.matches(".*\\d.*")
            || !password.matches(".*[^A-Za-z0-9].*"))
            throw new IllegalArgumentException("Password must be at least 12 characters and include uppercase, lowercase, a number, and a special character.");
    }
    private String clean(String value) { return value == null ? "" : value.trim(); }
}