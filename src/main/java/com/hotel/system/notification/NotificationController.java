package com.hotel.system.notification;

import jakarta.servlet.http.HttpServletRequest;
import com.hotel.system.account.AccountRole;
import com.hotel.system.account.SystemUserRepository;
import com.hotel.system.audit.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class NotificationController {
    private final NotificationService notifications;
    private final SystemUserRepository users;
    private final AuditService audit;

    public NotificationController(NotificationService notifications) {
        this(notifications, null, null);
    }

    @Autowired
    public NotificationController(NotificationService notifications, SystemUserRepository users, AuditService audit) {
        this.notifications = notifications;
        this.users = users;
        this.audit = audit;
    }

    @PreAuthorize("!hasRole('ADMIN') or @adminPermissionService.isEnabled('NOTIFICATION_MANAGE')")
    @GetMapping({"/customer/notifications", "/admin/notifications", "/super-admin/notifications"})
    public String inbox(Authentication authentication, HttpServletRequest request, Model model) {
        String portal = portalFrom(request);
        model.addAttribute("notifications", notifications.inbox(authentication.getName()));
        model.addAttribute("portal", portal);
        return "notifications/inbox";
    }

    @PreAuthorize("!hasRole('ADMIN') or @adminPermissionService.isEnabled('NOTIFICATION_MANAGE')")
    @PostMapping({"/customer/notifications/{id}/read", "/admin/notifications/{id}/read", "/super-admin/notifications/{id}/read"})
    public String markRead(@PathVariable Long id, Authentication authentication,
                           HttpServletRequest request, RedirectAttributes redirect) {
        String portal = portalFrom(request);
        try { notifications.markRead(id, authentication.getName()); }
        catch (IllegalArgumentException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/" + portal + "/notifications";
    }

    @PreAuthorize("@adminPermissionService.isEnabled('NOTIFICATION_MANAGE')")
    @PostMapping("/admin/notifications/send")
    public String sendToCustomer(@RequestParam String username, @RequestParam String title,
                                 @RequestParam String message, Authentication authentication,
                                 RedirectAttributes redirect) {
        try {
            if (users == null || audit == null) throw new IllegalStateException("Notification management is unavailable.");
            String target = username == null ? "" : username.trim();
            String cleanTitle = title == null ? "" : title.trim();
            String cleanMessage = message == null ? "" : message.trim();
            var customer = users.findByUsernameIgnoreCase(target)
                    .filter(user -> user.getRole() == AccountRole.CUSTOMER && user.isActive())
                    .orElseThrow(() -> new IllegalArgumentException("An active customer account was not found."));
            if (cleanTitle.isBlank() || cleanTitle.length() > 120
                    || cleanMessage.isBlank() || cleanMessage.length() > 2000)
                throw new IllegalArgumentException("Enter a title up to 120 characters and a message up to 2,000 characters.");
            notifications.notifyUser(customer.getUsername(), cleanTitle, cleanMessage, "ADMIN");
            audit.record(authentication.getName(), "CUSTOMER_NOTIFICATION_SENT", "SYSTEM_USER",
                    customer.getId().toString(), "Admin sent a customer notification.");
            redirect.addFlashAttribute("success", "Notification sent to " + customer.getUsername() + ".");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/notifications";
    }

    private String portalFrom(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/super-admin/")) return "super-admin";
        if (uri.startsWith("/admin/")) return "admin";
        return "customer";
    }
}