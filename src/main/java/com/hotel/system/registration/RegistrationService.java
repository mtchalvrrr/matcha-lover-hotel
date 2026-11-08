package com.hotel.system.registration;

import com.hotel.system.account.*;
import com.hotel.system.audit.AuditService;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class RegistrationService {
    private static final List<RegistrationStatus> ACTIVE_APPLICATIONS =
        List.of(RegistrationStatus.PENDING_OTP, RegistrationStatus.PENDING_REVIEW);
    private final RegistrationRepository applications;
    private final SystemUserRepository users;
    private final RegistrationDocumentStorage documents;
    private final PasswordEncoder encoder;
    private final JavaMailSender mail;
    private final Clock clock;
    private final AuditService audit;
    private final String fromAddress;
    private final SecureRandom random = new SecureRandom();

    public RegistrationService(RegistrationRepository applications, SystemUserRepository users,
        RegistrationDocumentStorage documents, PasswordEncoder encoder, JavaMailSender mail,
        Clock clock, AuditService audit, @Value("${spring.mail.username:}") String fromAddress) {
        this.applications = applications; this.users = users; this.documents = documents;
        this.encoder = encoder; this.mail = mail; this.clock = clock; this.audit = audit;
        this.fromAddress = fromAddress;
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

        String path = documents.store(idFile);
        String otp = String.format("%06d", random.nextInt(1_000_000));
        RegistrationApplication application = applications.save(new RegistrationApplication(first, last,
            address, user, encoder.encode(password), path, encoder.encode(otp),
            Instant.now(clock).plus(Duration.ofMinutes(10))));
        try {
            if (fromAddress == null || fromAddress.isBlank()) throw new IllegalStateException("Email delivery is not configured.");
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(address);
            message.setSubject("[HOTEL NAME] — Email verification code");
            message.setText("Your email verification code is " + otp + ". It expires in 10 minutes. If you did not request this, ignore this email.");
            mail.send(message);
        } catch (RuntimeException ex) {
            applications.delete(application);
            throw new IllegalStateException("We could not send the verification code. Email delivery must be configured before registration.", ex);
        }
        audit.record(user, "CUSTOMER_REGISTRATION_SUBMITTED", "REGISTRATION", application.getId().toString(),
            "Application submitted; email OTP sent.");
        return application;
    }

    public boolean verify(Long id, String otp) {
        RegistrationApplication app = applications.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Registration application not found."));
        if (otp == null) return false;
        boolean verified = app.verifyOtp(otp.trim(), encoder, Instant.now(clock));
        if (verified) audit.record(app.getUsername(), "REGISTRATION_EMAIL_VERIFIED", "REGISTRATION", id.toString(),
            "Email OTP verified; awaiting Super Admin review.");
        return verified;
    }


    public List<RegistrationApplication> pendingReview() {
        return applications.findByStatusOrderBySubmittedAtAsc(RegistrationStatus.PENDING_REVIEW);
    }

    public void review(Long id, boolean approve, String reviewer, String reason) {
        RegistrationApplication app = applications.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Registration application not found."));
        app.review(approve, reviewer, reason);
        if (approve) {
            if (users.existsByUsernameIgnoreCase(app.getUsername()) || users.existsByEmailIgnoreCase(app.getEmail()))
                throw new IllegalStateException("A matching account already exists; resolve the duplicate before approval.");
            users.save(new SystemUser(app.getFirstName(), app.getLastName(), app.getUsername(), app.getEmail(), app.getPasswordHash(), AccountRole.CUSTOMER));
        }
        audit.record(reviewer, approve ? "CUSTOMER_REGISTRATION_APPROVED" : "CUSTOMER_REGISTRATION_REJECTED",
            "REGISTRATION", id.toString(), "Applicant " + app.getUsername() + ". " + clean(reason));
    }

    public RegistrationApplication get(Long id) {
        return applications.findById(id).orElseThrow(() -> new IllegalArgumentException("Registration application not found."));
    }

    public java.nio.file.Path documentPath(Long id) {
        return documents.resolve(get(id).getIdDocumentPath());
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 12 || !password.matches(".*[A-Z].*")
            || !password.matches(".*[a-z].*") || !password.matches(".*\\d.*")
            || !password.matches(".*[^A-Za-z0-9].*"))
            throw new IllegalArgumentException("Password must be at least 12 characters and include uppercase, lowercase, a number, and a special character.");
    }
    private String clean(String value) { return value == null ? "" : value.trim(); }
}