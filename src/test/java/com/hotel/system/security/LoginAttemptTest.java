package com.hotel.system.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptTest {
    @Test
    void locksAfterFiveFailuresWithinTheWindow() {
        Instant now = Instant.parse("2026-11-08T03:00:00Z");
        LoginAttempt attempt = new LoginAttempt("guest", now);

        for (int i = 0; i < 4; i++) {
            attempt.fail(now.plusSeconds(i));
            assertFalse(attempt.isLocked(now.plusSeconds(i)));
        }

        attempt.fail(now.plusSeconds(4));

        assertEquals(5, attempt.getFailedCount());
        assertTrue(attempt.isLocked(now.plusSeconds(5)));
        assertFalse(attempt.isLocked(now.plusSeconds(904)));
    }

    @Test
    void resetsFailureWindowAfterFifteenMinutes() {
        Instant now = Instant.parse("2026-11-08T03:00:00Z");
        LoginAttempt attempt = new LoginAttempt("guest", now);
        for (int i = 0; i < 5; i++) attempt.fail(now.plusSeconds(i));

        Instant afterWindow = now.plusSeconds(901);
        attempt.fail(afterWindow);

        assertEquals(1, attempt.getFailedCount());
        assertFalse(attempt.isLocked(afterWindow));
    }

    @Test
    void successfulLoginClearsFailureCountAndLock() {
        Instant now = Instant.parse("2026-11-08T03:00:00Z");
        LoginAttempt attempt = new LoginAttempt("guest", now);
        for (int i = 0; i < 5; i++) attempt.fail(now.plusSeconds(i));

        attempt.clear(now.plusSeconds(6));

        assertEquals(0, attempt.getFailedCount());
        assertFalse(attempt.isLocked(now.plusSeconds(7)));
    }
}
