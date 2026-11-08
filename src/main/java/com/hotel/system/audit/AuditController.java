package com.hotel.system.audit;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
public class AuditController {
    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping("/super-admin/audit")
    public String auditLog(@RequestParam(required = false) String actor,
                           @RequestParam(required = false) String action,
                           @RequestParam(required = false) LocalDate from,
                           @RequestParam(required = false) LocalDate to,
                           Model model,
                           RedirectAttributes redirect) {
        try {
            model.addAttribute("events", audit.searchEvents(actor, action, from, to));
            model.addAttribute("actor", actor);
            model.addAttribute("action", action);
            model.addAttribute("from", from);
            model.addAttribute("to", to);
            return "super-admin/audit";
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/super-admin/audit";
        }
    }
}
