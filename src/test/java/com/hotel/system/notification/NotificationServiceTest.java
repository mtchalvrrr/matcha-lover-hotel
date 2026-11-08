package com.hotel.system.notification;

import com.hotel.system.account.AccountRole;
import com.hotel.system.account.SystemUser;
import com.hotel.system.account.SystemUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;

import static org.mockito.Mockito.*;

class NotificationServiceTest {
    @Test
    void eventKeyPreventsDuplicateReminderDelivery() {
        NotificationRepository notifications = mock(NotificationRepository.class);
        SystemUserRepository users = mock(SystemUserRepository.class);
        NotificationService service = new NotificationService(
                notifications, users, mock(JavaMailSender.class), "", "", "[HOTEL NAME]");
        when(notifications.existsByEventKey("CHECKIN:10:guest")).thenReturn(false, true);

        service.notifyUserOnce("guest", "Upcoming check-in", "Reminder", "RESERVATION", "CHECKIN:10:guest");
        service.notifyUserOnce("guest", "Upcoming check-in", "Reminder", "RESERVATION", "CHECKIN:10:guest");

        verify(notifications, times(1)).save(any(Notification.class));
        verify(users, times(1)).findByUsernameIgnoreCase("guest");
    }

    @Test
    void walkInRemindersAreSentToBothAdminAccountsWithSeparateDeduplicationKeys() {
        NotificationRepository notifications = mock(NotificationRepository.class);
        SystemUserRepository users = mock(SystemUserRepository.class);
        NotificationService service = new NotificationService(
                notifications, users, mock(JavaMailSender.class), "", "", "[HOTEL NAME]");
        SystemUser onlineAdmin = new SystemUser("online-admin", "online@example.com", "encoded", AccountRole.ADMIN);
        SystemUser walkInAdmin = new SystemUser("walkin-admin", "walkin@example.com", "encoded", AccountRole.ADMIN);
        when(users.findByRoleInOrderByCreatedAtDesc(List.of(AccountRole.ADMIN)))
                .thenReturn(List.of(onlineAdmin, walkInAdmin));
        when(notifications.existsByEventKey(anyString())).thenReturn(false);

        service.notifyAdminsOnce("Upcoming check-in", "Reminder", "RESERVATION", "CHECKIN:10");

        verify(notifications, times(2)).save(any(Notification.class));
    }
}
