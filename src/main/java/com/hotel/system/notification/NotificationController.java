package com.hotel.system.notification;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications) { this.notifications = notifications; }

    @GetMapping({"/customer/notifications", "/admin/notifications", "/super-admin/notifications"})
    public String inbox(Authentication authentication, HttpServletRequest request, Model model) {
        String portal = portalFrom(request);
        model.addAttribute("notifications", notifications.inbox(authentication.getName()));
        model.addAttribute("portal", portal);
        return "notifications/inbox";
    }

    @PostMapping({"/customer/notifications/{id}/read", "/admin/notifications/{id}/read", "/super-admin/notifications/{id}/read"})
    public String markRead(@PathVariable Long id, Authentication authentication,
                           HttpServletRequest request, RedirectAttributes redirect) {
        String portal = portalFrom(request);
        try { notifications.markRead(id, authentication.getName()); }
        catch (IllegalArgumentException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/" + portal + "/notifications";
    }

    private String portalFrom(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/super-admin/")) return "super-admin";
        if (uri.startsWith("/admin/")) return "admin";
        return "customer";
    }
}