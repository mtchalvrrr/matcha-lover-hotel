package com.hotel.system.reservation;

import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.hotel.system.payment.PaymentService;
import com.hotel.system.payment.XenditService;
import com.hotel.system.room.RoomService;
import com.hotel.system.room.RoomRatingService;

import java.time.LocalDate;
import java.time.Clock;

@Controller
public class ReservationController {
    private final ReservationService reservations;
    private final PaymentService payments;
    private final XenditService xendit;
    private final WalkInBookingService walkInBookings;
    private final RoomService rooms;
    private final Clock clock;
    private final ReservationHistoryService history;
    private final RoomRatingService roomRatings;

    public ReservationController(ReservationService reservations, PaymentService payments,
                                 XenditService xendit, WalkInBookingService walkInBookings, RoomService rooms, Clock clock,
                                 ReservationHistoryService history, RoomRatingService roomRatings) {
        this.reservations = reservations;
        this.payments = payments;
        this.xendit = xendit;
        this.walkInBookings = walkInBookings;
        this.rooms = rooms;
        this.clock = clock;
        this.history = history;
        this.roomRatings = roomRatings;
    }

    @PostMapping("/customer/reservations")
    public String requestReservation(@RequestParam Long roomId,
                                     @RequestParam LocalDate checkIn,
                                     @RequestParam LocalDate checkOut,
                                     @RequestParam Integer guests,
                                     Authentication authentication,
                                     RedirectAttributes redirect) {
        Reservation hold = null;
        try {
            hold = reservations.request(authentication.getName(), roomId, checkIn, checkOut, guests);
            String checkoutUrl = xendit.createCheckout(hold.getId(), authentication.getName());
            return "redirect:" + checkoutUrl;
        } catch (RuntimeException ex) {
            if (hold != null) {
                try { reservations.cancelUnpaidCheckout(hold.getId(), authentication.getName()); }
                catch (RuntimeException ignored) { /* Keep the original checkout error for the customer. */ }
            }
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/rooms/" + roomId;
        }
    }

    @PostMapping("/customer/reservations/{id}/checkout")
    public String continuePaymentCheckout(@PathVariable Long id, Authentication authentication,
                                          RedirectAttributes redirect) {
        try {
            return "redirect:" + payMongo.createCheckout(id, authentication.getName());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/customer/reservations";
        }
    }

    @GetMapping("/customer/reservations/{id}/history")
    public String customerReservationHistory(@PathVariable Long id, Authentication authentication,
                                             Model model, RedirectAttributes redirect) {
        try {
            model.addAttribute("history", history.customerHistory(id, authentication.getName()));
            return "reservations/history";
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/customer/reservations";
        }
    }

    @GetMapping("/admin/reservations/{id}/history")
    public String adminReservationHistory(@PathVariable Long id, Model model,
                                          RedirectAttributes redirect) {
        try {
            model.addAttribute("history", history.adminHistory(id));
            return "reservations/history";
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/reservations";
        }
    }

    @GetMapping("/customer/reservations")
    public String customerReservations(Authentication authentication, Model model) {
        var customerReservations = reservations.customerReservations(authentication.getName());
        model.addAttribute("reservations", customerReservations);
        model.addAttribute("ratingsByReservation", roomRatings.ratingsForReservations(
                customerReservations.stream().map(Reservation::getId).toList()));
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


    @PreAuthorize("@adminPermissionService.isEnabled('RESERVATION_EDIT')")
    @GetMapping("/admin/reservations/{id}/edit")
    public String adminEditForm(@PathVariable Long id, Model model, RedirectAttributes redirect) {
        try {
            Reservation reservation = reservations.adminReservationForModification(id);
            model.addAttribute("reservation", reservation);
            model.addAttribute("rooms", rooms.publicRooms());
            model.addAttribute("minimumDate", LocalDate.now(clock));
            return "admin/reservations/edit";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/reservations";
        }
    }

    @PreAuthorize("@adminPermissionService.isEnabled('RESERVATION_EDIT')")
    @PostMapping("/admin/reservations/{id}/edit")
    public String adminEditReservation(@PathVariable Long id, @RequestParam Long roomId,
            @RequestParam LocalDate checkIn, @RequestParam LocalDate checkOut,
            @RequestParam Integer guests, Authentication authentication, RedirectAttributes redirect) {
        try {
            reservations.adminModifyReservation(id, authentication.getName(), roomId, checkIn, checkOut, guests);
            redirect.addFlashAttribute("success", "Reservation #" + id + " updated. Payment safeguards and availability checks passed.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/reservations/" + id + "/edit";
        }
        return "redirect:/admin/reservations";
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

    @PostMapping("/admin/reservations/{id}/cancel")
    public String cancelByAdmin(@PathVariable Long id, @RequestParam String reason,
                                Authentication authentication, RedirectAttributes redirect) {
        try {
            reservations.cancelByAdmin(id, authentication.getName(), reason);
            redirect.addFlashAttribute("success", "Reservation cancelled.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/reservations";
    }

    @PostMapping("/admin/reservations/{id}/review")
    public String reviewReservation(@PathVariable Long id, @RequestParam boolean approve,
                                    @RequestParam(required = false) String reason,
                                    Authentication authentication, RedirectAttributes redirect) {
        try {
            reservations.review(id, approve, authentication.getName(), reason);
            redirect.addFlashAttribute("success", approve ? "Reservation approved." : "Reservation rejected.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/reservations";
    }
}