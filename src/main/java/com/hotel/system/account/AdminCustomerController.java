package com.hotel.system.account;

import com.hotel.system.registration.RegistrationService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Files;

@Controller
public class AdminCustomerController {
    private final AccountManagementService accounts;
    private final RegistrationService registrations;

    public AdminCustomerController(AccountManagementService accounts, RegistrationService registrations) {
        this.accounts = accounts;
        this.registrations = registrations;
    }

    @GetMapping("/admin/customers")
    public String customers(Model model) {
        model.addAttribute("customers", accounts.customerAccounts());
        model.addAttribute("applications", registrations.approvedApplications());
        return "admin/customers/list";
    }

    @PostMapping("/admin/customers/{id}/status")
    public String changeStatus(@PathVariable Long id, @RequestParam boolean active,
                               Authentication authentication, RedirectAttributes redirect) {
        try {
            accounts.setAccountActive(authentication.getName(), id, active);
            redirect.addFlashAttribute("success", active ? "Customer account reactivated." : "Customer account suspended.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/customers";
    }

    @GetMapping("/admin/customers/applications/{id}/document")
    @ResponseBody
    public ResponseEntity<FileSystemResource> document(@PathVariable Long id) throws java.io.IOException {
        java.nio.file.Path path = registrations.approvedDocumentPath(id);
        if (!Files.exists(path)) return ResponseEntity.notFound().build();
        String lower = path.toString().toLowerCase(java.util.Locale.ROOT);
        MediaType type = lower.endsWith(".pdf") ? MediaType.APPLICATION_PDF
                : lower.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"customer-identity-document\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new FileSystemResource(path));
    }
}
