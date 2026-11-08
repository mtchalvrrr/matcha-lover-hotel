package com.hotel.system.registration;

import com.hotel.system.account.SystemUserRepository;
import com.hotel.system.audit.AuditService;
import com.hotel.system.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationStatusLookupTest {
    @Test
    void returnsApplicationOnlyWhenReferenceAndEmailBothMatch() {
        RegistrationRepository applications = mock(RegistrationRepository.class);
        RegistrationApplication application = mock(RegistrationApplication.class);
        when(applications.findByIdAndEmailIgnoreCase(12L, "guest@example.com"))
                .thenReturn(Optional.of(application));
        RegistrationService service = service(applications);

        assertSame(application, service.findStatus(12L, " GUEST@example.com "));
        verify(applications).findByIdAndEmailIgnoreCase(12L, "guest@example.com");
    }

    @Test
    void doesNotRevealApplicationWhenEmailDoesNotMatchReference() {
        RegistrationRepository applications = mock(RegistrationRepository.class);
        when(applications.findByIdAndEmailIgnoreCase(12L, "other@example.com"))
                .thenReturn(Optional.empty());
        RegistrationService service = service(applications);

        assertThrows(IllegalArgumentException.class,
                () -> service.findStatus(12L, "other@example.com"));
    }

    private RegistrationService service(RegistrationRepository applications) {
        return new RegistrationService(applications, mock(SystemUserRepository.class),
                mock(RegistrationDocumentStorage.class), new BCryptPasswordEncoder(),
                mock(JavaMailSender.class),
                Clock.fixed(Instant.parse("2026-11-08T03:00:00Z"), ZoneId.of("Asia/Manila")),
                mock(AuditService.class), "noreply@example.com", mock(NotificationService.class));
    }
}
