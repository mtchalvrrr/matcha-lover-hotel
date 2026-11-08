package com.hotel.system.security;

import com.hotel.system.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
public class LoginAttemptService {
    private final LoginAttemptRepository attempts;
    private final Clock clock;
    private final AuditService audit;

    public LoginAttemptService(LoginAttemptRepository attempts, Clock clock, AuditService audit) {
        this.attempts = attempts;
        this.clock = clock;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public boolean isLocked(String username) {
        if (!isUsableUsername(username)) return false;
        return attempts.findByUsernameIgnoreCase(normalize(username))
                .map(attempt -> attempt.isLocked(Instant.now(clock)))
                .orElse(false);
    }

    @Transactional
    public void recordFailure(String username) {
        if (!isUsableUsername(username)) return;
        String normalized = normalize(username);
        Instant now = Instant.now(clock);
        LoginAttempt attempt = attempts.findByUsernameIgnoreCase(normalized)
                .orElseGet(() -> new LoginAttempt(normalized, now));
        attempt.fail(now);
        attempts.save(attempt);
        audit.record(normalized, attempt.isLocked(now) ? "LOGIN_LOCKED" : "LOGIN_FAILED",
                "AUTHENTICATION", normalized,
                attempt.isLocked(now)
                        ? "Repeated failed sign-in attempts triggered a temporary account lock."
                        : "A sign-in attempt failed.");
    }

    @Transactional
    public void recordSuccess(String username) {
        if (!isUsableUsername(username)) return;
        String normalized = normalize(username);
        attempts.findByUsernameIgnoreCase(normalized)
                .ifPresent(attempt -> {
                    attempt.clear(Instant.now(clock));
                    attempts.save(attempt);
                });
        audit.record(normalized, "LOGIN_SUCCEEDED", "AUTHENTICATION", normalized,
                "Successful portal sign-in.");
    }

    private boolean isUsableUsername(String username) {
        return username != null && !username.isBlank() && username.trim().length() <= 80;
    }

    private String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
