package com.hotel.system.security;

import com.hotel.system.audit.AuditService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginAttemptServiceTest {
    private final LoginAttemptRepository attempts = mock(LoginAttemptRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-08T03:00:00Z"), ZoneId.of("Asia/Manila"));

    @Test
    void recordsFailedAndLockedSignInsForSuperAdminAudit() {
        LoginAttempt attempt = new LoginAttempt("guest", Instant.now(clock));
        when(attempts.findByUsernameIgnoreCase("guest")).thenReturn(Optional.of(attempt));
        LoginAttemptService service = new LoginAttemptService(attempts, clock, audit);

        for (int i = 0; i < 5; i++) service.recordFailure("guest");

        verify(audit, times(4)).record("guest", "LOGIN_FAILED", "AUTHENTICATION",
                "guest", "A sign-in attempt failed.");
        verify(audit).record("guest", "LOGIN_LOCKED", "AUTHENTICATION", "guest",
                "Repeated failed sign-in attempts triggered a temporary account lock.");
    }

    @Test
    void successfulSignInClearsCounterAndCreatesAuditEvent() {
        LoginAttempt attempt = new LoginAttempt("guest", Instant.now(clock));
        attempt.fail(Instant.now(clock));
        when(attempts.findByUsernameIgnoreCase("guest")).thenReturn(Optional.of(attempt));
        LoginAttemptService service = new LoginAttemptService(attempts, clock, audit);

        service.recordSuccess("guest");

        assertEquals(0, attempt.getFailedCount());
        verify(audit).record("guest", "LOGIN_SUCCEEDED", "AUTHENTICATION",
                "guest", "Successful portal sign-in.");
    }

    @Test
    void ignoresOverlongUsernamesWithoutWritingAuditOrLockoutRows() {
        LoginAttemptService service = new LoginAttemptService(attempts, clock, audit);

        service.recordFailure("x".repeat(81));

        verifyNoInteractions(attempts, audit);
    }
}
