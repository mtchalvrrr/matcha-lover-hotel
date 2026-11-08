package com.hotel.system.account;

import com.hotel.system.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordRecoverySessionInvalidationTest {
    @Test
    void passwordResetExpiresEveryExistingSessionForTheAccount() throws Exception {
        Instant now = Instant.parse("2026-11-08T03:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("Asia/Manila"));
        SystemUserRepository users = mock(SystemUserRepository.class);
        PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
        JavaMailSender mail = mock(JavaMailSender.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuditService audit = mock(AuditService.class);
        SessionRegistry sessions = mock(SessionRegistry.class);

        SystemUser account = new SystemUser("guest", "guest@example.com", "old-hash", AccountRole.CUSTOMER);
        PasswordResetToken token = new PasswordResetToken(7L, hash("raw-reset-token"), now.plusSeconds(600));
        var principal = User.withUsername("guest").password("old-hash").roles("CUSTOMER").build();
        SessionInformation activeSession = mock(SessionInformation.class);

        when(tokens.findByTokenHash(hash("raw-reset-token"))).thenReturn(Optional.of(token));
        when(users.findById(7L)).thenReturn(Optional.of(account));
        when(encoder.encode("New-Strong-Password-123!")).thenReturn("new-hash");
        when(sessions.getAllPrincipals()).thenReturn(List.of(principal));
        when(sessions.getAllSessions(principal, false)).thenReturn(List.of(activeSession));

        PasswordRecoveryService service = new PasswordRecoveryService(users, tokens, mail, encoder,
                clock, audit, sessions, "");

        service.reset("raw-reset-token", "New-Strong-Password-123!");

        assertEquals("new-hash", account.getPasswordHash());
        assertEquals(now, token.getUsedAt());
        verify(activeSession).expireNow();
        verify(audit).record("guest", "PASSWORD_RESET_COMPLETED", "SYSTEM_USER", nullSafeId(account),
                "Password reset completed.");
    }

    private static String hash(String value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String nullSafeId(SystemUser account) {
        return account.getId() == null ? "null" : account.getId().toString();
    }
}
