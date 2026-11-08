package com.hotel.system.reservation;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.hotel.system.payment.PaymentService;
import com.hotel.system.room.RoomService;

import java.time.LocalDate;
import java.time.Clock;

@Controller
public class ReservationController {
    private final ReservationService reservations;
    private final PaymentService payments;
    private final WalkInBookingService walkInBookings;
    private final RoomService rooms;
    private final Clock clock;

    public ReservationController(ReservationService reservations, PaymentService payments,
                                 WalkInBookingService walkInBookings, RoomService rooms, Clock clock) {
        this.reservations = reservations;
        this.payments = payments;
        this.walkInBookings = walkInBookings;
        this.rooms = rooms;
        this.clock = clock;
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

    @GetMapping("/customer/reservations/{id}/modify")
    public String modifyForm(@PathVariable Long id, Authentication authentication, Model model) {
        try {
            model.addAttribute("reservation", reservations.customerReservationForModification(id, authentication.getName()));
            model.addAttribute("rooms", rooms.publicRooms());
            model.addAttribute("minimumDate", LocalDate.now(clock));
            return "customer/reservation-modify";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("reservations", reservations.customerReservations(authentication.getName()));
            return "customer/reservations";
        }
    }

    @PostMapping("/customer/reservations/{id}/modify")
    public String modifyReservation(@PathVariable Long id, @RequestParam Long roomId,
                                    @RequestParam LocalDate checkIn, @RequestParam LocalDate checkOut,
                                    @RequestParam Integer guests, Authentication authentication,
                                    RedirectAttributes redirect) {
        try {
            reservations.modifyCustomerReservation(id, authentication.getName(), roomId, checkIn, checkOut, guests);
            redirect.addFlashAttribute("success", "Reservation changes submitted. The updated request is pending administrator approval.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/customer/reservations/" + id + "/modify";
        }
        return "redirect:/customer/reservations";
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
            WalkInBookingService.WalkInBookingResult booking = walkInBookings.create(
                authentication.getName(), guestName, guestEmail, roomId, checkIn, checkOut, guests, paymentMethod);
            Reservation reservation = booking.reservation();
            var payment = booking.payment();
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
    public String adminReservations(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) ReservationStatus status,
                                    @RequestParam(required = false) ReservationSource source,
                                    @RequestParam(required = false) LocalDate from,
                                    @RequestParam(required = false) LocalDate to,
                                    Model model, RedirectAttributes redirect) {
        try {
            model.addAttribute("reservations", reservations.searchReservations(q, status, source, from, to));
            model.addAttribute("q", q);
            model.addAttribute("selectedStatus", status);
            model.addAttribute("selectedSource", source);
            model.addAttribute("from", from);
            model.addAttribute("to", to);
            model.addAttribute("statuses", ReservationStatus.values());
            model.addAttribute("sources", ReservationSource.values());
            return "admin/reservations/list";
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/reservations";
        }
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