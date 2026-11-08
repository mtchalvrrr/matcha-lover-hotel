package com.hotel.system.reservation;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
public class ReservationController {
    private final ReservationService reservations;

    public ReservationController(ReservationService reservations) {
        this.reservations = reservations;
    }

    @PostMapping("/customer/reservations")
    public String requestReservation(@RequestParam Long roomId,
                                     @RequestParam LocalDate checkIn,
                                     @RequestParam LocalDate checkOut,
                                     @RequestParam Integer guests,
                                     Authentication authentication,
                                     RedirectAttributes redirect) {
        try {
            Reservation reservation = reservations.request(authentication.getName(), roomId, checkIn, checkOut, guests);
            redirect.addFlashAttribute("success", "Reservation request #" + reservation.getId() + " was submitted for approval.");
            return "redirect:/customer/reservations";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/rooms/" + roomId;
        }
    }

    @GetMapping("/customer/reservations")
    public String customerReservations(Authentication authentication, Model model) {
        model.addAttribute("reservations", reservations.customerReservations(authentication.getName()));
        return "customer/reservations";
    }

    @PostMapping("/customer/reservations/{id}/cancel")
    public String cancelReservation(@PathVariable Long id, Authentication authentication,
                                    RedirectAttributes redirect) {
        try {
            reservations.cancelCustomerReservation(id, authentication.getName());
            redirect.addFlashAttribute("success", "Reservation cancelled.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/customer/reservations";
    }

    @GetMapping("/admin/reservations")
    public String adminReservations(Model model) {
        model.addAttribute("reservations", reservations.allReservations());
        return "admin/reservations/list";
    }

    @PostMapping("/admin/reservations/{id}/review")
    public String reviewReservation(@PathVariable Long id, @RequestParam boolean approve,
                                    RedirectAttributes redirect) {
        try {
            reservations.review(id, approve);
            redirect.addFlashAttribute("success", approve ? "Reservation approved." : "Reservation rejected.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/reservations";
    }
}