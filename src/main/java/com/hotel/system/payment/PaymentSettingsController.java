package com.hotel.system.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PaymentSettingsController {
    private final String mode;
    private final boolean apiKeyConfigured;
    private final boolean webhookTokenConfigured;

    public PaymentSettingsController(
            @Value("${payment.mode:SIMULATED}") String mode,
            @Value("${payment.xendit.secret-key:}") String apiKey,
            @Value("${payment.xendit.webhook-token:}") String webhookToken) {
        this.mode = mode;
        this.apiKeyConfigured = apiKey != null && apiKey.trim().startsWith("xnd_development_");
        this.webhookTokenConfigured = webhookToken != null && !webhookToken.isBlank();
    }

    @GetMapping("/super-admin/payment-settings")
    public String paymentSettings(Model model) {
        boolean ready = "XENDIT_TEST".equalsIgnoreCase(mode)
                && apiKeyConfigured && webhookTokenConfigured;
        model.addAttribute("paymentMode", mode);
        model.addAttribute("apiKeyConfigured", apiKeyConfigured);
        model.addAttribute("webhookTokenConfigured", webhookTokenConfigured);
        model.addAttribute("xenditReady", ready);
        return "super-admin/payment-settings";
    }
}
