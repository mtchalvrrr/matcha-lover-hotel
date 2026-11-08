package com.hotel.system.reservation;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.hotel.system.payment.PaymentService;
import com.hotel.system.room.RoomService;

import java.time.LocalDate;

@Controller
public class ReservationController {
    private final ReservationService reservations;
    private final PaymentService payments;
    private final RoomService rooms;

    public ReservationController(ReservationService reservations, PaymentService payments, RoomService rooms) {
        this.reservations = reservations;
        this.payments = payments;
        this.rooms = rooms;
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


    @GetMapping("/admin/reservations/walk-in")
    public String walkInForm(Model model) {
        model.addAttribute("rooms", rooms.publicRooms());
        return "admin/reservations/walk-in";
    }

    @PostMapping("/admin/reservations/walk-in")
    public String createWalkIn(@RequestParam String guestName, @RequestParam String guestEmail,
        @RequestParam Long roomId, @RequestParam LocalDate checkIn, @RequestParam LocalDate checkOut,
        @RequestParam Integer guests, @RequestParam String paymentMethod,
        Authentication authentication, RedirectAttributes redirect) {
        try {
            Reservation reservation = reservations.createWalkIn(authentication.getName(), guestName,
                guestEmail, roomId, checkIn, checkOut, guests);
            var payment = payments.recordWalkInPayment(reservation.getId(), paymentMethod, authentication.getName());
            redirect.addFlashAttribute("success", "Walk-in reservation #" + reservation.getId()
                + " created and onsite payment recorded. Receipt reference: " + payment.getTransactionReference());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/reservations/walk-in";
        }
        return "redirect:/admin/reservations";
    }

    @PostMapping("/admin/reservations/{id}/check-in")
    public String checkIn(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
        try { reservations.checkIn(id, authentication.getName()); redirect.addFlashAttribute("success", "Guest checked in."); }
        catch (IllegalArgumentException | IllegalStateException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/reservations";
    }

    @PostMapping("/admin/reservations/{id}/check-out")
    public String checkOut(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
        try { reservations.checkOut(id, authentication.getName()); redirect.addFlashAttribute("success", "Guest checked out."); }
        catch (IllegalArgumentException | IllegalStateException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/reservations";
    }

    @PostMapping("/admin/reservations/{id}/no-show")
    public String noShow(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
        try { reservations.markNoShow(id, authentication.getName()); redirect.addFlashAttribute("success", "Reservation marked as no-show."); }
        catch (IllegalArgumentException | IllegalStateException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/reservations";
    }

    @GetMapping("/admin/reservations")
    public String adminReservations(Model model) {
        model.addAttribute("reservations", reservations.allReservations());
        return "admin/reservations/list";
    }

    @PostMapping("/admin/reservations/{id}/review")
    public String reviewReservation(@PathVariable Long id, @RequestParam boolean approve,
                                    Authentication authentication, RedirectAttributes redirect) {
        try {
            reservations.review(id, approve, authentication.getName());
            redirect.addFlashAttribute("success", approve ? "Reservation approved." : "Reservation rejected.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/reservations";
    }
}