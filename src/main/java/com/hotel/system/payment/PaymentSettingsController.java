package com.hotel.system.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PaymentSettingsController {
    private final String mode;
    private final boolean apiKeyConfigured;
    private final boolean webhookSecretConfigured;

    public PaymentSettingsController(
            @Value("${payment.mode:SIMULATED}") String mode,
            @Value("${payment.paymongo.secret-key:}") String apiKey,
            @Value("${payment.paymongo.webhook-secret:}") String webhookSecret) {
        this.mode = mode;
        this.apiKeyConfigured = apiKey != null && apiKey.trim().startsWith("sk_test_");
        this.webhookSecretConfigured = webhookSecret != null && !webhookSecret.isBlank();
    }

    @GetMapping("/super-admin/payment-settings")
    public String paymentSettings(Model model) {
        model.addAttribute("paymentMode", mode);
        model.addAttribute("apiKeyConfigured", apiKeyConfigured);
        model.addAttribute("webhookSecretConfigured", webhookSecretConfigured);
        model.addAttribute("payMongoReady", "PAYMONGO_TEST".equalsIgnoreCase(mode)
                && apiKeyConfigured && webhookSecretConfigured);
        return "super-admin/payment-settings";
    }
}
