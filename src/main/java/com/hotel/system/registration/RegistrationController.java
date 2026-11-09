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
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.UUID;

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


    @PostMapping("/register/send-otp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendOtp(@RequestParam String firstName,
            @RequestParam String lastName, @RequestParam String email, @RequestParam String password,
            HttpSession session) {
        try {
            String username = "guest" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            RegistrationApplication app = registrations.submit(firstName, lastName, email, username, password, null);
            session.setAttribute("registrationApplicationId", app.getId());
            session.setAttribute("registrationEmail", app.getEmail());
            session.setAttribute("registrationOtpVerified", false);
            return ResponseEntity.ok(Map.of("success", true, "message", "A six-digit OTP was sent to your email. It expires in five minutes."));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/register/resend-otp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resendOtp(HttpSession session) {
        try {
            Long id = (Long) session.getAttribute("registrationApplicationId");
            String email = (String) session.getAttribute("registrationEmail");
            if (id == null || email == null) throw new IllegalStateException("Send an OTP first.");
            registrations.resendOtp(id, email);
            return ResponseEntity.ok(Map.of("success", true, "message", "A new six-digit OTP was sent."));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/register/verify-email-otp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifyEmailOtp(@RequestParam String otp, HttpSession session) {
        try {
            Long id = (Long) session.getAttribute("registrationApplicationId");
            String email = (String) session.getAttribute("registrationEmail");
            if (id == null || email == null) throw new IllegalStateException("Send an OTP first.");
            if (!registrations.verify(id, email, otp))
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "The OTP is incorrect. Check the six digits and try again."));
            session.setAttribute("registrationOtpVerified", true);
            return ResponseEntity.ok(Map.of("success", true, "message", "Email verified successfully."));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/register/identity")
    public String submitIdentity(@RequestParam String idType, @RequestParam MultipartFile idFront,
            @RequestParam MultipartFile idBack, @RequestParam(required = false) String privacyAcknowledged,
            HttpSession session, RedirectAttributes redirect) {
        try {
            Long id = (Long) session.getAttribute("registrationApplicationId");
            String email = (String) session.getAttribute("registrationEmail");
            Boolean verified = (Boolean) session.getAttribute("registrationOtpVerified");
            if (id == null || email == null || !Boolean.TRUE.equals(verified))
                throw new IllegalStateException("Complete email verification before submitting registration.");
            if (!"yes".equals(privacyAcknowledged))
                throw new IllegalStateException("Acknowledge the Privacy Notice before submitting.");
            if (captcha == null || !captcha.isEnabled())
                throw new IllegalStateException("reCAPTCHA is not configured. Registration cannot be submitted.");
            var attributes = RequestContextHolder.getRequestAttributes();
            if (!(attributes instanceof ServletRequestAttributes servlet)
                    || !captcha.verify(servlet.getRequest().getParameter("g-recaptcha-response"),
                            servlet.getRequest().getRemoteAddr()))
                throw new IllegalStateException("Complete the reCAPTCHA check before submitting.");
            registrations.attachIdentityDocuments(id, email, idType, idFront, idBack);
            session.removeAttribute("registrationApplicationId");
            session.removeAttribute("registrationEmail");
            session.removeAttribute("registrationOtpVerified");
            redirect.addFlashAttribute("success", "Registration submitted. Your verified email and ID documents are awaiting review.");
            return "redirect:/";
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
            return "redirect:/";
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
    public ResponseEntity<FileSystemResource> document(@PathVariable Long id, @RequestParam(defaultValue = "front") String side) throws java.io.IOException {
        java.nio.file.Path path = registrations.documentPath(id, side);
        if (!Files.exists(path)) return ResponseEntity.notFound().build();
        MediaType type = path.toString().toLowerCase().endsWith(".pdf") ? MediaType.APPLICATION_PDF :
            (path.toString().toLowerCase().endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG);
        return ResponseEntity.ok().contentType(type)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"identity-document\"")
            .body(new FileSystemResource(path));
    }
}