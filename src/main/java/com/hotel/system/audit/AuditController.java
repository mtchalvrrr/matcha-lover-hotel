package com.hotel.system.audit;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuditController {
    private final AuditService audit;
    public AuditController(AuditService audit) { this.audit = audit; }

    @GetMapping("/super-admin/audit")
    public String auditLog(Model model) {
        model.addAttribute("events", audit.recentEvents());
        return "super-admin/audit";
    }
}