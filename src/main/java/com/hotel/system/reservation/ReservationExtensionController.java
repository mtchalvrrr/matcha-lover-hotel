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
    private final ReservationService reservations;

    public ReservationExtensionController(ReservationExtensionService extensions, ReservationService reservations) {
        this.extensions = extensions;
        this.reservations = reservations;
    }

    @GetMapping("/customer/reservations/{id}/extension")
    public String extensionForm(@PathVariable Long id, Authentication authentication,
                                Model model, RedirectAttributes redirect) {
        try {
            Reservation reservation = reservations.customerReservationForModification(id, authentication.getName());
            if (reservation.getStatus() != ReservationStatus.APPROVED
                    && reservation.getStatus() != ReservationStatus.CHECKED_IN)
                throw new IllegalStateException("Only approved or checked-in reservations can be extended.");
            model.addAttribute("reservation", reservation);
            model.addAttribute("extensionRequests", extensions.customerRequests(authentication.getName()).stream()
                    .filter(request -> request.getReservationId().equals(id)).toList());
            return "customer/reservation-extension";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/customer/reservations";
        }
    }

    @PostMapping("/customer/reservations/{id}/extension")
    public String requestExtension(@PathVariable Long id, @RequestParam LocalDate newCheckout,
                                   Authentication authentication, RedirectAttributes redirect) {
        try {
            extensions.request(id, authentication.getName(), newCheckout);
            redirect.addFlashAttribute("success", "Extension request submitted for administrator approval.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/customer/reservations/" + id + "/extension";
        }
        return "redirect:/customer/reservations";
    }

    @GetMapping("/admin/reservation-extensions")
    public String extensionRequests(Model model) {
        model.addAttribute("extensionRequests", extensions.allRequests());
        return "admin/reservations/extensions";
    }

    @PostMapping("/admin/reservation-extensions/{id}/review")
    public String reviewExtension(@PathVariable Long id, @RequestParam boolean approve,
                                  @RequestParam(required = false) String reason,
                                  Authentication authentication, RedirectAttributes redirect) {
        try {
            extensions.review(id, approve, authentication.getName(), reason);
            redirect.addFlashAttribute("success", approve ? "Extension approved and reservation total updated."
                    : "Extension request rejected.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/reservation-extensions";
    }
}
