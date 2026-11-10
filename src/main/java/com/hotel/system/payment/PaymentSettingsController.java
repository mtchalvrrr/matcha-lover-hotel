package com.hotel.system.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PaymentSettingsController {
    private final String mode;
    private final boolean apiKeyConfigured;
    private final boolean webhookTokenConfigured;
    private final boolean publicHttpsUrlConfigured;
    private final PaymentProviderSettingsService settings;

    public PaymentSettingsController(
            PaymentProviderSettingsService settings,
            @Value("${payment.mode:SIMULATED}") String mode,
            @Value("${payment.xendit.secret-key:}") String apiKey,
            @Value("${payment.xendit.webhook-token:}") String webhookToken,
            @Value("${hotel.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.settings = settings;
        this.mode = mode;
        this.apiKeyConfigured = apiKey != null && apiKey.trim().startsWith("xnd_development_");
        this.webhookTokenConfigured = webhookToken != null && !webhookToken.isBlank();
        this.publicHttpsUrlConfigured = publicBaseUrl != null && publicBaseUrl.startsWith("https://");
    }

    @GetMapping("/super-admin/payment-settings")
    public String paymentSettings(Model model) {
        boolean ready = settings.onlinePaymentsEnabled()
                && "XENDIT_TEST".equalsIgnoreCase(mode)
                && apiKeyConfigured && webhookTokenConfigured && publicHttpsUrlConfigured;
        model.addAttribute("paymentMode", mode);
        model.addAttribute("apiKeyConfigured", apiKeyConfigured);
        model.addAttribute("webhookTokenConfigured", webhookTokenConfigured);
        model.addAttribute("publicHttpsUrlConfigured", publicHttpsUrlConfigured);
        model.addAttribute("onlinePaymentsEnabled", settings.onlinePaymentsEnabled());
        model.addAttribute("xenditReady", ready);
        return "super-admin/payment-settings";
    }

    /** Super Admin can enable or disable online checkout without editing provider secrets. */
    @PostMapping("/super-admin/payment-settings/online-payments")
    public String updateOnlinePayments(@RequestParam(required = false) Boolean enabled,
            Authentication authentication, RedirectAttributes redirect) {
        try {
            boolean allowOnlinePayments = Boolean.TRUE.equals(enabled);
            settings.updateOnlinePayments(allowOnlinePayments, authentication.getName());
            redirect.addFlashAttribute("success",
                    allowOnlinePayments ? "Online payment checkout enabled." : "Online payment checkout disabled.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/super-admin/payment-settings";
    }
}