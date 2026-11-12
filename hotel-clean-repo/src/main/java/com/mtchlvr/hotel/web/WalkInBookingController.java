package com.mtchlvr.hotel.web;

import com.mtchlvr.hotel.model.Payment;
import com.mtchlvr.hotel.model.Reservation;
import com.mtchlvr.hotel.model.Room;
import com.mtchlvr.hotel.repo.PaymentRepository;
import com.mtchlvr.hotel.repo.ReservationRepository;
import com.mtchlvr.hotel.repo.RoomRepository;
import com.mtchlvr.hotel.service.WalkInBookingService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Web endpoints for On-Site Manager walk-in reservations.
 * Walk-in guests are recorded separately and never need AppUser accounts.
 */
@Controller
@Validated
public class WalkInBookingController {
    private final WalkInBookingService walkInBookingService;
    private final RoomRepository rooms;
    private final ReservationRepository reservations;
    private final PaymentRepository payments;

    public WalkInBookingController(
            WalkInBookingService walkInBookingService,
            RoomRepository rooms,
            ReservationRepository reservations,
            PaymentRepository payments) {
        this.walkInBookingService = walkInBookingService;
        this.rooms = rooms;
        this.reservations = reservations;
        this.payments = payments;
    }

    @GetMapping("/manager/walk-ins/new")
    public String newWalkInBooking(Model model) {
        model.addAttribute("rooms",
                rooms.findByActiveTrueAndAvailableTrueOrderByTypeAscRoomNumberAsc());
        model.addAttribute("form", new WalkInForm());
        return "walk-in-booking";
    }

    @PostMapping("/manager/walk-ins")
    public String createWalkInBooking(
            @RequestParam @NotBlank @Size(max = 150) String fullName,
            @RequestParam(required = false) @Email @Size(max = 160) String email,
            @RequestParam(required = false) @Size(max = 40) String phone,
            @RequestParam Long roomId,
            @RequestParam LocalDate checkIn,
            @RequestParam LocalTime checkInTime,
            @RequestParam LocalDate checkOut,
            @RequestParam LocalTime checkOutTime,
            RedirectAttributes flash) {
        try {
            Reservation saved = walkInBookingService.createWalkInBooking(
                    fullName, email, phone, roomId,
                    checkIn, checkInTime, checkOut, checkOutTime);
            flash.addFlashAttribute("success",
                    "Walk-in reservation #" + saved.getId()
                            + " was recorded and fully paid.");
            return "redirect:/manager/walk-ins/" + saved.getId() + "/receipt";
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            return "redirect:/manager/walk-ins/new";
        }
    }

    @GetMapping("/manager/walk-ins/{id}/receipt")
    public String walkInReceipt(@PathVariable Long id, Model model) {
        Reservation reservation = reservations.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Walk-in reservation not found."));
        if (!"WALK_IN".equals(reservation.getBookingType())
                || reservation.getWalkInGuest() == null
                || reservation.getCustomer() != null) {
            throw new IllegalArgumentException("This is not a valid walk-in reservation.");
        }
        List<Payment> paymentRecords =
                payments.findAllByReservationIdOrderByPaidAtAsc(id);
        model.addAttribute("reservation", reservation);
        model.addAttribute("payments", paymentRecords);
        model.addAttribute("fullPaymentRecorded", paymentRecords.stream().anyMatch(p ->
                "WALK_IN_FULL_PAYMENT".equals(p.getPaymentType())
                        && "PAID".equals(p.getStatus())
                        && p.getAmount() != null
                        && p.getAmount().compareTo(reservation.getTotalCost()) == 0));
        return "walk-in-receipt";
    }

    public static class WalkInForm {
        private String fullName;
        private String email;
        private String phone;
        private Long roomId;
        private LocalDate checkIn;
        private LocalTime checkInTime = LocalTime.of(14, 0);
        private LocalDate checkOut;
        private LocalTime checkOutTime = LocalTime.NOON;

        public String getFullName() { return fullName; }
        public void setFullName(String value) { fullName = value; }
        public String getEmail() { return email; }
        public void setEmail(String value) { email = value; }
        public String getPhone() { return phone; }
        public void setPhone(String value) { phone = value; }
        public Long getRoomId() { return roomId; }
        public void setRoomId(Long value) { roomId = value; }
        public LocalDate getCheckIn() { return checkIn; }
        public void setCheckIn(LocalDate value) { checkIn = value; }
        public LocalTime getCheckInTime() { return checkInTime; }
        public void setCheckInTime(LocalTime value) { checkInTime = value; }
        public LocalDate getCheckOut() { return checkOut; }
        public void setCheckOut(LocalDate value) { checkOut = value; }
        public LocalTime getCheckOutTime() { return checkOutTime; }
        public void setCheckOutTime(LocalTime value) { checkOutTime = value; }
    }
}
