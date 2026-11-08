package com.hotel.system.account;

import org.springframework.beans.factory.annotation.Value;
import java.net.URI;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordRecoveryController {
    private final PasswordRecoveryService recovery;
    private final String publicBaseUrl;

    public PasswordRecoveryController(PasswordRecoveryService recovery,
            @Value("${hotel.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.recovery = recovery;
        this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
    }

    @GetMapping("/forgot-password")
    public String forgot() {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String request(@RequestParam String email, RedirectAttributes redirect) {
        try {
            recovery.request(email, publicBaseUrl);
        } catch (IllegalStateException ignored) {
            // Keep the response generic so callers cannot discover whether an account exists.
        }
        redirect.addFlashAttribute("success",
                "If an account uses that email, password recovery instructions will be sent.");
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPage(@RequestParam String token, Model model) {
        model.addAttribute("token", token);
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String reset(@RequestParam String token, @RequestParam String newPassword,
                        RedirectAttributes redirect) {
        try {
            recovery.reset(token, newPassword);
            redirect.addFlashAttribute("success", "Password reset. You can now sign in.");
            return "redirect:/customer/login";
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/reset-password?token=" + token;
        }
    }

    private String normalizeBaseUrl(String value) {
        String clean = value == null ? "" : value.trim();
        while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        try {
            URI uri = URI.create(clean);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException();
            }
            return clean;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                    "HOTEL_PUBLIC_BASE_URL must be a valid absolute HTTP(S) URL without credentials, query, or fragment.", ex);
        }
    }
}}
