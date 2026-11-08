package com.hotel.system.registration;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.mockito.Mockito.*;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationApplicationTest {
    @Test
    void otpIsExpiredAtTheExactExpiryInstant() {
        Instant expiry = Instant.parse("2026-11-08T00:10:00Z");
        RegistrationApplication application = new RegistrationApplication(
                "Guest", "User", "guest@example.com", "guest-user", "password-hash",
                "private/id.png", "document-hash", "otp-hash", expiry);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.matches("123456", "otp-hash")).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> application.verifyOtp("123456", encoder, expiry));
        verifyNoInteractions(encoder);
    }

    @Test
    void otpCanBeUsedImmediatelyBeforeItsExpiryInstant() {
        Instant expiry = Instant.parse("2026-11-08T00:10:00Z");
        RegistrationApplication application = new RegistrationApplication(
                "Guest", "User", "guest@example.com", "guest-user", "password-hash",
                "private/id.png", "document-hash", "otp-hash", expiry);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.matches("123456", "otp-hash")).thenReturn(true);

        assertTrue(application.verifyOtp("123456", encoder, expiry.minusNanos(1)));
    }

    @Test
    void otpResendEnforcesCooldownAndResetsExpiry() {
        Instant now = Instant.parse("2026-11-08T00:00:00Z");
        RegistrationApplication application = new RegistrationApplication(
                "Guest", "User", "guest@example.com", "guest-user", "password-hash",
                "private/id.png", "document-hash", "old-otp-hash", now.plusSeconds(600));

        assertThrows(IllegalStateException.class,
                () -> application.resendOtp("new-hash", now.plusSeconds(59)));

        application.resendOtp("new-hash", now.plusSeconds(60));

        assertThrows(IllegalStateException.class,
                () -> application.resendOtp("third-hash", now.plusSeconds(90)));
    }

    @Test
    void otpResendStopsAfterThreeReplacementCodes() {
        Instant now = Instant.parse("2026-11-08T00:00:00Z");
        RegistrationApplication application = new RegistrationApplication(
                "Guest", "User", "guest@example.com", "guest-user", "password-hash",
                "private/id.png", "document-hash", "old-otp-hash", now.plusSeconds(600));

        application.resendOtp("hash-1", now.plusSeconds(60));
        application.resendOtp("hash-2", now.plusSeconds(120));
        application.resendOtp("hash-3", now.plusSeconds(180));

        assertThrows(IllegalStateException.class,
                () -> application.resendOtp("hash-4", now.plusSeconds(240)));
    }
}
