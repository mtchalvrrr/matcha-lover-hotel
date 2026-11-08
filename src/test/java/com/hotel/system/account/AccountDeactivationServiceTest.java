package com.hotel.system.account;

import com.hotel.system.audit.AuditService;
import com.hotel.system.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountDeactivationServiceTest {
    @Test
    void approvedRequestDisablesCustomerAndExpiresActiveSessions() {
        AccountDeactivationRequestRepository requests = mock(AccountDeactivationRequestRepository.class);
        SystemUserRepository users = mock(SystemUserRepository.class);
        AuditService audit = mock(AuditService.class);
        NotificationService notifications = mock(NotificationService.class);
        SessionRegistry sessions = mock(SessionRegistry.class);
        AccountDeactivationService service = new AccountDeactivationService(
                requests, users, audit, notifications, sessions);

        SystemUser customer = new SystemUser("guest", "guest@example.com", "encoded", AccountRole.CUSTOMER);
        AccountDeactivationRequest request = new AccountDeactivationRequest(1L, "guest", "No longer needed.");
        var principal = User.withUsername("guest").password("encoded").authorities("ROLE_CUSTOMER").build();
        SessionInformation session = new SessionInformation(principal, "session-1", Instant.now());

        when(requests.findById(100L)).thenReturn(Optional.of(request));
        when(users.findById(1L)).thenReturn(Optional.of(customer));
        when(sessions.getAllPrincipals()).thenReturn(List.of(principal));
        when(sessions.getAllSessions(principal, false)).thenReturn(List.of(session));

        service.review(100L, true, "superadmin", null);

        assertFalse(customer.isActive());
        assertEquals(DeactivationRequestStatus.APPROVED, request.getStatus());
        assertTrue(session.isExpired());
        verify(notifications).notifyUser(eq("guest"), eq("Account deactivated"), anyString(), eq("ACCOUNT"));
    }

    @Test
    void rejectionRequiresAReason() {
        AccountDeactivationRequestRepository requests = mock(AccountDeactivationRequestRepository.class);
        SystemUserRepository users = mock(SystemUserRepository.class);
        AccountDeactivationService service = new AccountDeactivationService(
                requests, users, mock(AuditService.class), mock(NotificationService.class),
                mock(SessionRegistry.class));
        AccountDeactivationRequest request = new AccountDeactivationRequest(1L, "guest", "Reason");
        when(requests.findById(100L)).thenReturn(Optional.of(request));

        assertThrows(IllegalArgumentException.class,
                () -> service.review(100L, false, "superadmin", " "));
    }
}
