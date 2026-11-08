package com.hotel.system.reservation;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
public class ReservationExtensionController {
    private final ReservationExtensionService extensions;

    public ReservationExtensionController(ReservationExtensionService extensions) {
        this.extensions = extensions;
    }

    @GetMapping("/customer/reservations/{id}/extensions")
    public String customerExtensions(@PathVariable Long id, Authentication authentication, Model model,
                                     RedirectAttributes redirect) {
        try {
            model.addAttribute("reservationId", id);
            model.addAttribute("requests", extensions.requestsForReservation(id, authentication.getName()));
            model.addAttribute("minimumCheckout", LocalDate.now().plusDays(1));
            return "customer/reservation-extensions";
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/customer/reservations";
        }
    }

    @PostMapping("/customer/reservations/{id}/extensions")
    public String requestExtension(@PathVariable Long id, @RequestParam LocalDate requestedCheckout,
                                   @RequestParam(required = false) String reason,
                                   Authentication authentication, RedirectAttributes redirect) {
        try {
            extensions.request(id, authentication.getName(), requestedCheckout, reason);
            redirect.addFlashAttribute("success", "Extension request submitted for administrator review.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/customer/reservations/" + id + "/extensions";
        }
        return "redirect:/customer/reservations/" + id + "/extensions";
    }

    @GetMapping("/admin/extensions")
    public String adminExtensions(Model model) {
        model.addAttribute("requests", extensions.allRequests());
        return "admin/reservations/extensions";
    }

    @PostMapping("/admin/extensions/{id}/review")
    public String reviewExtension(@PathVariable Long id, @RequestParam boolean approve,
                                  @RequestParam(required = false) String reason,
                                  Authentication authentication, RedirectAttributes redirect) {
        try {
            extensions.review(id, approve, authentication.getName(), reason);
            redirect.addFlashAttribute("success", approve
                    ? "Extension approved and reservation checkout updated."
                    : "Extension request rejected.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/extensions";
    }
}
