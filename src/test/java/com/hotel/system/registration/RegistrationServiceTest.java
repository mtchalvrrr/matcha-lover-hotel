package com.hotel.system.registration;

import com.hotel.system.account.SystemUserRepository;
import com.hotel.system.audit.AuditService;
import com.hotel.system.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegistrationServiceTest {

    @Test
    void removesStoredIdDocumentWhenVerificationEmailCannotBeSent() {
        RegistrationRepository applications = mock(RegistrationRepository.class);
        SystemUserRepository users = mock(SystemUserRepository.class);
        RegistrationDocumentStorage documents = mock(RegistrationDocumentStorage.class);
        JavaMailSender mail = mock(JavaMailSender.class);
        RegistrationApplication saved = mock(RegistrationApplication.class);
        RegistrationService service = new RegistrationService(applications, users, documents,
                new BCryptPasswordEncoder(), mail,
                Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneId.of("Asia/Manila")),
                mock(AuditService.class), "noreply@example.com", mock(NotificationService.class));
        MockMultipartFile idFile = new MockMultipartFile(
                "idDocument", "identity.png", "image/png", new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        String storedPath = "/tmp/uploads/private-registration-documents/123.png";

        when(users.existsByEmailIgnoreCase("guest@example.com")).thenReturn(false);
        when(applications.existsByEmailIgnoreCaseAndStatusIn(eq("guest@example.com"), anyList())).thenReturn(false);
        when(users.existsByUsernameIgnoreCase("guest-user")).thenReturn(false);
        when(applications.existsByUsernameIgnoreCaseAndStatusIn(eq("guest-user"), anyList())).thenReturn(false);
        when(applications.existsByDocumentHashAndStatusIn(anyString(), anyList())).thenReturn(false);
        when(documents.store(idFile)).thenReturn(storedPath);
        when(applications.save(any(RegistrationApplication.class))).thenReturn(saved);
        doThrow(new MailSendException("SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));

        assertThrows(IllegalStateException.class, () -> service.submit(
                "Guest", "User", "guest@example.com", "guest-user", "StrongPassword123!", idFile));

        verify(applications).delete(saved);
        verify(documents).delete(storedPath);
    }

    @Test
    void rejectsAnIdDocumentAlreadyUsedByAnActiveOrApprovedApplication() {
        RegistrationRepository applications = mock(RegistrationRepository.class);
        SystemUserRepository users = mock(SystemUserRepository.class);
        RegistrationDocumentStorage documents = mock(RegistrationDocumentStorage.class);
        RegistrationService service = new RegistrationService(applications, users, documents,
                new BCryptPasswordEncoder(), mock(JavaMailSender.class),
                Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneId.of("Asia/Manila")),
                mock(AuditService.class), "noreply@example.com", mock(NotificationService.class));
        MockMultipartFile idFile = new MockMultipartFile(
                "idDocument", "identity.png", "image/png", new byte[]{1, 2, 3, 4, 5});

        when(users.existsByEmailIgnoreCase("guest@example.com")).thenReturn(false);
        when(applications.existsByEmailIgnoreCaseAndStatusIn(eq("guest@example.com"), anyList())).thenReturn(false);
        when(users.existsByUsernameIgnoreCase("guest-user")).thenReturn(false);
        when(applications.existsByUsernameIgnoreCaseAndStatusIn(eq("guest-user"), anyList())).thenReturn(false);
        when(applications.existsByDocumentHashAndStatusIn(anyString(), anyList())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.submit(
                "Guest", "User", "guest@example.com", "guest-user",
                "StrongPassword123!", idFile));
        verify(documents, never()).store(any());
    }
    @Test
    void allowsRegistrationWithoutAnIdDocumentDuringTesting() {
        RegistrationRepository applications = mock(RegistrationRepository.class);
        SystemUserRepository users = mock(SystemUserRepository.class);
        RegistrationDocumentStorage documents = mock(RegistrationDocumentStorage.class);
        JavaMailSender mail = mock(JavaMailSender.class);
        RegistrationApplication saved = mock(RegistrationApplication.class);
        when(users.existsByEmailIgnoreCase("guest@example.com")).thenReturn(false);
        when(applications.existsByEmailIgnoreCaseAndStatusIn(eq("guest@example.com"), anyList())).thenReturn(false);
        when(users.existsByUsernameIgnoreCase("guest-user")).thenReturn(false);
        when(applications.existsByUsernameIgnoreCaseAndStatusIn(eq("guest-user"), anyList())).thenReturn(false);
        when(applications.save(any(RegistrationApplication.class))).thenReturn(saved);
        when(saved.getId()).thenReturn(42L);

        RegistrationService service = new RegistrationService(applications, users, documents,
                new BCryptPasswordEncoder(), mail,
                Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneId.of("Asia/Manila")),
                mock(AuditService.class), "noreply@example.com", mock(NotificationService.class));

        assertSame(saved, service.submit("Guest", "User", "guest@example.com",
                "guest-user", "StrongPassword123!", null));
        verify(documents, never()).store(any());
        verify(mail).send(any(SimpleMailMessage.class));
    }

    @Test
    void successfulEmailVerificationActivatesCustomerWithoutSuperAdminReview() {
        RegistrationRepository applications = mock(RegistrationRepository.class);
        SystemUserRepository users = mock(SystemUserRepository.class);
        RegistrationApplication application = mock(RegistrationApplication.class);
        NotificationService notifications = mock(NotificationService.class);
        when(applications.findByIdAndEmailIgnoreCase(42L, "guest@example.com"))
                .thenReturn(java.util.Optional.of(application));
        when(application.verifyOtp(eq("123456"), any(), any())).thenReturn(true);
        when(application.getUsername()).thenReturn("guest-user");
        when(application.getEmail()).thenReturn("guest@example.com");
        when(application.getFirstName()).thenReturn("Guest");
        when(application.getLastName()).thenReturn("User");
        when(application.getPasswordHash()).thenReturn("encoded-password");
        when(users.existsByUsernameIgnoreCase("guest-user")).thenReturn(false);
        when(users.existsByEmailIgnoreCase("guest@example.com")).thenReturn(false);

        RegistrationService service = new RegistrationService(applications, users,
                mock(RegistrationDocumentStorage.class), new BCryptPasswordEncoder(),
                mock(JavaMailSender.class),
                Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneId.of("Asia/Manila")),
                mock(AuditService.class), "noreply@example.com", notifications);

        assertTrue(service.verify(42L, "guest@example.com", "123456"));
        verify(users).save(any(com.hotel.system.account.SystemUser.class));
        verify(application).review(true, "SYSTEM",
                "Automatically activated after email verification (temporary testing mode).");
        verify(notifications).notifyUser(eq("guest-user"), eq("Customer account activated"),
                contains("now active"), eq("ACCOUNT"));
        verify(notifications, never()).notifySuperAdmins(anyString(), anyString(), anyString());
    }

}
