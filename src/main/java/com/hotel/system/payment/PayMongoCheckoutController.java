package com.hotel.system.payment;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PayMongoCheckoutController {
    private final PayMongoService payMongo;

    public PayMongoCheckoutController(PayMongoService payMongo) {
        this.payMongo = payMongo;
    }

    @PostMapping("/customer/reservations/{id}/pay-online")
    public String checkout(@PathVariable Long id, Authentication authentication,
                           RedirectAttributes redirect) {
        try {
            return "redirect:" + payMongo.createCheckout(id, authentication.getName());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/customer/reservations";
        }
    }
}
