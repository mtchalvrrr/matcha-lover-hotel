package com.hotel.system.account;

import com.hotel.system.audit.AuditLog;
import com.hotel.system.audit.AuditService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class SuperAdminMonitoringController {
    private final AuditService audit;
    private final SystemUserRepository users;

    public SuperAdminMonitoringController(AuditService audit, SystemUserRepository users) {
        this.audit = audit;
        this.users = users;
    }

    /** Login success, failure, and temporary lock events are recorded by LoginAttemptService. */
    @GetMapping("/super-admin/security-events")
    public String securityEvents(@RequestParam(required = false) String actor,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to, Model model) {
        List<AuditLog> events = audit.searchEvents(actor, "LOGIN_", from, to);
        model.addAttribute("events", events);
        model.addAttribute("actor", actor);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "super-admin/security-events";
    }

    /** Displays audit actions performed by the two Admin accounts, regardless of booking channel. */
    @GetMapping("/super-admin/admin-activity")
    public String adminActivity(@RequestParam(required = false) String actor,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to, Model model) {
        Set<String> adminNames = users.findByRoleInOrderByCreatedAtDesc(List.of(AccountRole.ADMIN))
                .stream().map(user -> user.getUsername().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<AuditLog> events = audit.searchEvents(actor, null, from, to).stream()
                .filter(event -> adminNames.contains(event.getActorUsername().toLowerCase(Locale.ROOT)))
                .toList();
        model.addAttribute("events", events);
        model.addAttribute("actor", actor);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "super-admin/admin-activity";
    }
}