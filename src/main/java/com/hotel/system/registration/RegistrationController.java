package com.hotel.system.registration;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.hotel.system.security.CaptchaService;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.nio.file.Files;

@Controller
public class RegistrationController {
    private final RegistrationService registrations;
    private final CaptchaService captcha;
    public RegistrationController(RegistrationService registrations) { this(registrations, null); }
    @Autowired
    public RegistrationController(RegistrationService registrations, CaptchaService captcha) {
        this.registrations = registrations;
        this.captcha = captcha;
    }

    @GetMapping("/register")
    public String register() { return "registration/form"; }

    @PostMapping("/register")
    public String submit(@RequestParam String firstName, @RequestParam String lastName,
        @RequestParam String email, @RequestParam String username, @RequestParam String password,
        @RequestParam(required = false) MultipartFile idDocument, RedirectAttributes redirect) {
        try {
            if (captcha != null && captcha.isEnabled()) {
                var attributes = RequestContextHolder.getRequestAttributes();
                if (!(attributes instanceof ServletRequestAttributes servlet)
                        || !captcha.verify(servlet.getRequest().getParameter("g-recaptcha-response"),
                                servlet.getRequest().getRemoteAddr())) {
                    throw new IllegalStateException("CAPTCHA verification failed. Please try again.");
                }
            }
            RegistrationApplication app = registrations.submit(firstName, lastName, email, username, password, idDocument);
            redirect.addFlashAttribute("applicationId", app.getId());
            redirect.addFlashAttribute("email", app.getEmail());
            redirect.addFlashAttribute("success", "Verification code sent to your email. Verify it to activate your customer account.");
            return "redirect:/register/verify";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/register";
        }
    }

    @GetMapping("/register/verify")
    public String verifyPage() { return "registration/verify"; }

    @PostMapping("/register/verify")
    public String verify(@RequestParam Long applicationId, @RequestParam String email,
                         @RequestParam String otp, RedirectAttributes redirect) {
        try {
            if (!registrations.verify(applicationId, email, otp)) {
                redirect.addFlashAttribute("applicationId", applicationId);
                redirect.addFlashAttribute("email", email);
                redirect.addFlashAttribute("error", "Verification code is incorrect.");
                return "redirect:/register/verify";
            }
            redirect.addFlashAttribute("success", "Email verified. Your customer account is now active. You can sign in.");
            return "redirect:/customer/login?registration=success";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("applicationId", applicationId);
            redirect.addFlashAttribute("email", email);
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/register/verify";
        }
    }

    @PostMapping("/register/verify/resend")
    public String resendOtp(@RequestParam Long applicationId, @RequestParam String email,
                             RedirectAttributes redirect) {
        try {
            registrations.resendOtp(applicationId, email);
            redirect.addFlashAttribute("applicationId", applicationId);
            redirect.addFlashAttribute("email", email);
            redirect.addFlashAttribute("success", "A new verification code was sent. It expires in 5 minutes.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("applicationId", applicationId);
            redirect.addFlashAttribute("email", email);
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/register/verify";
    }


    @GetMapping("/registration/status")
    public String registrationStatusForm() {
        return "registration/status-form";
    }

    @PostMapping("/registration/status")
    public String registrationStatus(@RequestParam Long applicationId,
                                     @RequestParam String email,
                                     Model model) {
        try {
            model.addAttribute("application", registrations.findStatus(applicationId, email));
            return "registration/status";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", "No matching registration application was found. Check your reference and email.");
            return "registration/status-form";
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