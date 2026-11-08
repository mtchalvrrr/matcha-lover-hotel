package com.hotel.system.security;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
public class LoginAttemptService {
    private final LoginAttemptRepository attempts;
    private final Clock clock;

    public LoginAttemptService(LoginAttemptRepository attempts, Clock clock) {
        this.attempts = attempts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isLocked(String username) {
        if (username == null || username.isBlank()) return false;
        return attempts.findByUsernameIgnoreCase(normalize(username))
                .map(attempt -> attempt.isLocked(Instant.now(clock)))
                .orElse(false);
    }

    @Transactional
    public void recordFailure(String username) {
        if (username == null || username.isBlank()) return;
        String normalized = normalize(username);
        Instant now = Instant.now(clock);
        LoginAttempt attempt = attempts.findByUsernameIgnoreCase(normalized)
                .orElseGet(() -> new LoginAttempt(normalized, now));
        attempt.fail(now);
        attempts.save(attempt);
    }

    @Transactional
    public void recordSuccess(String username) {
        if (username == null || username.isBlank()) return;
        attempts.findByUsernameIgnoreCase(normalize(username))
                .ifPresent(attempt -> {
                    attempt.clear(Instant.now(clock));
                    attempts.save(attempt);
                });
    }

    private String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
