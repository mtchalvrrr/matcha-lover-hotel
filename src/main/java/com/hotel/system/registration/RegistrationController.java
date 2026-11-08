package com.hotel.system.registration;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.nio.file.Files;

@Controller
public class RegistrationController {
    private final RegistrationService registrations;
    public RegistrationController(RegistrationService registrations) { this.registrations = registrations; }

    @GetMapping("/register")
    public String register() { return "registration/form"; }

    @PostMapping("/register")
    public String submit(@RequestParam String firstName, @RequestParam String lastName,
        @RequestParam String email, @RequestParam String username, @RequestParam String password,
        @RequestParam MultipartFile idDocument, RedirectAttributes redirect) {
        try {
            RegistrationApplication app = registrations.submit(firstName, lastName, email, username, password, idDocument);
            redirect.addFlashAttribute("applicationId", app.getId());
            redirect.addFlashAttribute("success", "Verification code sent to your email. Enter it to submit your application for review.");
            return "redirect:/register/verify";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/register";
        }
    }

    @GetMapping("/register/verify")
    public String verifyPage() { return "registration/verify"; }

    @PostMapping("/register/verify")
    public String verify(@RequestParam Long applicationId, @RequestParam String otp, RedirectAttributes redirect) {
        try {
            if (!registrations.verify(applicationId, otp)) {
                redirect.addFlashAttribute("applicationId", applicationId);
                redirect.addFlashAttribute("error", "Verification code is incorrect.");
                return "redirect:/register/verify";
            }
            redirect.addFlashAttribute("success", "Email verified. Your application is now waiting for Super Admin review.");
            return "redirect:/customer/login?registration=pending";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("applicationId", applicationId);
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/register/verify";
        }
    }

    @GetMapping("/super-admin/registrations")
    public String pendingApplications(Model model) {
        model.addAttribute("applications", registrations.pendingReview());
        return "super-admin/registrations/list";
    }

    @PostMapping("/super-admin/registrations/{id}/review")
    public String review(@PathVariable Long id, @RequestParam boolean approve,
        @RequestParam(required = false) String reason, Authentication authentication,
        RedirectAttributes redirect) {
        try {
            registrations.review(id, approve, authentication.getName(), reason);
            redirect.addFlashAttribute("success", approve ? "Customer account approved and activated." : "Application rejected.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/super-admin/registrations";
    }

    @GetMapping("/super-admin/registrations/{id}/document")
    @ResponseBody
    public ResponseEntity<FileSystemResource> document(@PathVariable Long id) throws java.io.IOException {
        java.nio.file.Path path = registrations.documentPath(id);
        if (!Files.exists(path)) return ResponseEntity.notFound().build();
        MediaType type = path.toString().toLowerCase().endsWith(".pdf") ? MediaType.APPLICATION_PDF :
            (path.toString().toLowerCase().endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG);
        return ResponseEntity.ok().contentType(type)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"identity-document\"")
            .body(new FileSystemResource(path));
    }
}