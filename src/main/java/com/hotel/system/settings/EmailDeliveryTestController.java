package com.hotel.system.settings;

import com.hotel.system.account.SystemUser;
import com.hotel.system.account.SystemUserRepository;
import com.hotel.system.audit.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class EmailDeliveryTestController {
    private final JavaMailSender mail;
    private final SystemUserRepository users;
    private final AuditService audit;
    private final String host;
    private final String fromAddress;
    private final String hotelName;

    public EmailDeliveryTestController(JavaMailSender mail, SystemUserRepository users, AuditService audit,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.username:}") String fromAddress,
            @Value("${hotel.name:[HOTEL NAME]}") String hotelName) {
        this.mail = mail;
        this.users = users;
        this.audit = audit;
        this.host = host;
        this.fromAddress = fromAddress;
        this.hotelName = hotelName;
    }

    @PostMapping("/super-admin/settings/test-email")
    public String sendTest(Authentication authentication, RedirectAttributes redirect) {
        try {
            if (host == null || host.isBlank() || fromAddress == null || fromAddress.isBlank())
                throw new IllegalStateException("SMTP host and sender address must be configured first.");
            SystemUser superAdmin = users.findByUsernameIgnoreCase(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Super Admin account was not found."));
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(superAdmin.getEmail());
            message.setSubject(hotelName + " — SMTP configuration test");
            message.setText("This is a test email from " + hotelName
                    + ". If you received it, the configured SMTP server accepted the message.");
            mail.send(message);
            audit.record(authentication.getName(), "SMTP_TEST_EMAIL_SENT", "SYSTEM_SETTINGS", "SMTP",
                    "Sent a configuration test email to the signed-in Super Admin address.");
            redirect.addFlashAttribute("success", "Test email sent to your Super Admin email address. Check your inbox and spam folder.");
        } catch (RuntimeException ex) {
            audit.record(authentication.getName(), "SMTP_TEST_EMAIL_FAILED", "SYSTEM_SETTINGS", "SMTP",
                    "SMTP test failed; error details were kept out of the user-facing message.");
            redirect.addFlashAttribute("error", "The SMTP test could not be sent. Check the mail host, port, username, password, and provider security settings.");
        }
        return "redirect:/super-admin/settings";
    }
}
