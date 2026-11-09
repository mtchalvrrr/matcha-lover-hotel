package com.hotel.system.payment;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class NexusPayCheckoutController {
    private final NexusPayService nexusPay;

    public NexusPayCheckoutController(NexusPayService nexusPay) {
        this.nexusPay = nexusPay;
    }

    @PostMapping("/customer/reservations/{id}/pay-online")
    public String checkout(@PathVariable Long id, Authentication authentication,
                           RedirectAttributes redirect) {
        try {
            return "redirect:" + nexusPay.createCheckout(id, authentication.getName());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/customer/reservations";
        }
    }
}
