package com.hotel.system.notification;

import com.hotel.system.account.AccountRole;
import com.hotel.system.account.SystemUser;
import com.hotel.system.account.SystemUserRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final SystemUserRepository users;
    private final JavaMailSender mail;
    private final String mailHost;
    private final String fromAddress;
    private final String hotelName;

    public NotificationService(NotificationRepository notifications, SystemUserRepository users,
                               JavaMailSender mail,
                               @Value("${spring.mail.host:}") String mailHost,
                               @Value("${spring.mail.username:}") String fromAddress,
                               @Value("${hotel.name:[HOTEL NAME]}") String hotelName) {
        this.notifications = notifications;
        this.users = users;
        this.mail = mail;
        this.mailHost = mailHost;
        this.fromAddress = fromAddress;
        this.hotelName = hotelName;
    }

    public void notifyUser(String username, String title, String message, String category) {
        if (username == null || username.isBlank()) return;
        notifications.save(new Notification(username, title, message, category));
        users.findByUsernameIgnoreCase(username).ifPresent(user -> sendEmailIfConfigured(user, title, message));
    }

    public void notifyAdmins(String title, String message, String category) {
        users.findByRoleInOrderByCreatedAtDesc(List.of(AccountRole.ADMIN))
                .forEach(admin -> notifyUser(admin.getUsername(), title, message, category));
    }

    public void notifySuperAdmins(String title, String message, String category) {
        users.findByRoleInOrderByCreatedAtDesc(List.of(AccountRole.SUPER_ADMIN))
                .forEach(admin -> notifyUser(admin.getUsername(), title, message, category));
    }

    @Transactional(readOnly = true)
    public List<Notification> inbox(String username) {
        return notifications.findByRecipientUsernameOrderByCreatedAtDesc(username);
    }

    public void markRead(Long id, String username) {
        Notification notification = notifications.findByIdAndRecipientUsername(id, username)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found."));
        notification.markRead();
    }

    private void sendEmailIfConfigured(SystemUser recipient, String title, String message) {
        if (mailHost == null || mailHost.isBlank() || fromAddress == null || fromAddress.isBlank()) return;
        try {
            SimpleMailMessage email = new SimpleMailMessage();
            email.setFrom(fromAddress);
            email.setTo(recipient.getEmail());
            email.setSubject(hotelName + " — " + title);
            email.setText(message + "\n\nThis is an automated notification from " + hotelName + ".");
            mail.send(email);
        } catch (RuntimeException ex) {
            // The in-app notification remains saved; SMTP failure must not undo a booking or payment.
            log.warn("Email notification could not be delivered for account {}", recipient.getUsername());
        }
    }
}
