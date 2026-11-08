package com.hotel.system.audit;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Controller
public class AuditController {
    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @PreAuthorize("@adminPermissionService.isEnabled('AUDIT_VIEW')")
    @GetMapping("/admin/audit")
    public String adminAuditLog(@RequestParam(required = false) String actor,
                                @RequestParam(required = false) String action,
                                @RequestParam(required = false) LocalDate from,
                                @RequestParam(required = false) LocalDate to, Model model,
                                RedirectAttributes redirect) {
        try {
            model.addAttribute("events", audit.searchEvents(actor, action, from, to));
            model.addAttribute("actor", actor);
            model.addAttribute("action", action);
            model.addAttribute("from", from);
            model.addAttribute("to", to);
            return "admin/audit";
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/audit";
        }
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
            model.addAttribute("integrity", audit.verifyIntegrity());
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

    @GetMapping("/super-admin/audit/export.csv")
    public ResponseEntity<byte[]> exportAuditCsv(@RequestParam(required = false) String actor,
                                                  @RequestParam(required = false) String action,
                                                  @RequestParam(required = false) LocalDate from,
                                                  @RequestParam(required = false) LocalDate to) {
        java.util.List<AuditLog> events;
        try {
            events = audit.searchEvents(actor, action, from, to);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(ex.getMessage().getBytes(StandardCharsets.UTF_8));
        }
        StringBuilder csv = new StringBuilder("\uFEFF");
        append(csv, "Timestamp", "Actor", "Action", "Target type", "Target ID", "Details");
        for (AuditLog event : events) {
            append(csv, event.getCreatedAt(), event.getActorUsername(), event.getAction(),
                    event.getTargetType(), event.getTargetId(), event.getDetails());
        }
        String filename = "hotel-audit-" + (from == null ? "all" : from)
                + "-to-" + (to == null ? "all" : to) + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private boolean startsWithSpreadsheetFormula(String value) {
        int index = 0;
        while (index < value.length()
                && (Character.isWhitespace(value.charAt(index))
                    || Character.isISOControl(value.charAt(index)))) {
            index++;
        }
        return index < value.length() && "=+-@".indexOf(value.charAt(index)) >= 0;
    }

    private void append(StringBuilder csv, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) csv.append(',');
            String value = values[i] == null ? "" : values[i].toString();
            if (startsWithSpreadsheetFormula(value)) {
                value = "'" + value;
            }
            csv.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
    }
}
