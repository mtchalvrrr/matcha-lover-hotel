package com.hotel.system.reservation;

import com.hotel.system.account.SystemUser;
import com.hotel.system.account.SystemUserRepository;
import com.hotel.system.audit.AuditService;
import com.hotel.system.payment.PaymentRepository;
import com.hotel.system.room.Room;
import com.hotel.system.room.RoomNotFoundException;
import com.hotel.system.room.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional
public class ReservationService {
    private static final List<ReservationStatus> BLOCKING_STATUSES =
        List.of(ReservationStatus.PENDING, ReservationStatus.APPROVED, ReservationStatus.CHECKED_IN);

    private final ReservationRepository reservations;
    private final RoomRepository rooms;
    private final SystemUserRepository users;
    private final Clock clock;
    private final AuditService audit;
    private final PaymentRepository payments;

    public ReservationService(ReservationRepository reservations, RoomRepository rooms,
                              SystemUserRepository users, Clock clock, AuditService audit, PaymentRepository payments) {
        this.reservations = reservations;
        this.rooms = rooms;
        this.users = users;
        this.clock = clock;
        this.audit = audit;
        this.payments = payments;
    }

    public Reservation request(String username, Long roomId, LocalDate checkIn,
                               LocalDate checkOut, Integer guests) {
        SystemUser customer = users.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new IllegalArgumentException("Customer account was not found."));
        Room room = rooms.findByIdAndActiveTrue(roomId)
            .orElseThrow(() -> new RoomNotFoundException("This room is not available."));
        validateDates(checkIn, checkOut);
        if (guests == null || guests < 1 || guests > room.getCapacity()) {
            throw new IllegalArgumentException("Guest count must be between 1 and the room capacity.");
        }
        if (isUnavailable(roomId, checkIn, checkOut)) {
            throw new IllegalArgumentException("This room is already reserved for some or all of those dates.");
        }
        int nights = Math.toIntExact(ChronoUnit.DAYS.between(checkIn, checkOut));
        BigDecimal total = room.getNightlyRate().multiply(BigDecimal.valueOf(nights));
        Reservation saved = reservations.save(new Reservation(customer.getId(), customer.getUsername(),
            customer.getEmail(), room.getId(), room.getRoomCode(), room.getName(),
            checkIn, checkOut, guests, nights, room.getNightlyRate(), total));
        audit.record(username, "RESERVATION_REQUESTED", "RESERVATION", saved.getId().toString(),
            "Room " + room.getRoomCode() + ", stay " + checkIn + " to " + checkOut);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Reservation> customerReservations(String username) {
        SystemUser customer = users.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new IllegalArgumentException("Customer account was not found."));
        return reservations.findByCustomerIdOrderByCreatedAtDesc(customer.getId());
    }

    @Transactional(readOnly = true)
    public List<Reservation> allReservations() {
        return reservations.findAllByOrderByCreatedAtDesc();
    }

    public void review(Long id, boolean approve, String adminUsername) {
        Reservation reservation = reservations.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new IllegalStateException("Only pending reservations can be approved or rejected.");
        }
        if (approve) {
            if (isUnavailable(reservation.getRoomId(), reservation.getCheckInDate(),
                              reservation.getCheckOutDate(), reservation.getId())) {
                throw new IllegalStateException("The room dates are no longer available.");
            }
            reservation.changeStatus(ReservationStatus.APPROVED);
            audit.record(adminUsername, "RESERVATION_APPROVED", "RESERVATION", id.toString(),
                "Approved reservation for " + reservation.getCustomerUsername());
        } else {
            reservation.changeStatus(ReservationStatus.REJECTED);
            audit.record(adminUsername, "RESERVATION_REJECTED", "RESERVATION", id.toString(),
                "Rejected reservation for " + reservation.getCustomerUsername());
        }
    }

    public void cancelCustomerReservation(Long id, String username) {
        SystemUser customer = users.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new IllegalArgumentException("Customer account was not found."));
        Reservation reservation = reservations.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (!reservation.getCustomerId().equals(customer.getId())) {
            throw new IllegalArgumentException("Reservation not found.");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING
                && reservation.getStatus() != ReservationStatus.APPROVED) {
            throw new IllegalStateException("This reservation can no longer be cancelled online.");
        }
        if (!reservation.getCheckInDate().isAfter(LocalDate.now(clock))) {
            throw new IllegalStateException("Reservations starting today or in the past require admin assistance.");
        }
        reservation.changeStatus(ReservationStatus.CANCELLED);
        audit.record(username, "RESERVATION_CANCELLED", "RESERVATION", id.toString(),
            "Customer cancelled reservation.");
    }


    public Reservation createWalkIn(String adminUsername, String guestName, String guestEmail,
                                    Long roomId, LocalDate checkIn, LocalDate checkOut, Integer guests) {
        String name = guestName == null ? "" : guestName.trim();
        String email = guestEmail == null ? "" : guestEmail.trim().toLowerCase();
        if (name.isBlank() || name.length() > 160) throw new IllegalArgumentException("Enter the guest's full name.");
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$") || email.length() > 254)
            throw new IllegalArgumentException("Enter a valid guest email address.");
        Room room = rooms.findByIdAndActiveTrue(roomId)
            .orElseThrow(() -> new RoomNotFoundException("This room is not available."));
        validateWalkInDates(checkIn, checkOut);
        if (guests == null || guests < 1 || guests > room.getCapacity())
            throw new IllegalArgumentException("Guest count must be between 1 and the room capacity.");
        if (isUnavailable(roomId, checkIn, checkOut))
            throw new IllegalArgumentException("This room is already reserved for some or all of those dates.");
        int nights = Math.toIntExact(ChronoUnit.DAYS.between(checkIn, checkOut));
        BigDecimal total = room.getNightlyRate().multiply(BigDecimal.valueOf(nights));
        Reservation saved = reservations.save(new Reservation(room.getId(), name, email, adminUsername,
            room.getRoomCode(), room.getName(), checkIn, checkOut, guests, nights, room.getNightlyRate(), total));
        audit.record(adminUsername, "WALK_IN_RESERVATION_CREATED", "RESERVATION", saved.getId().toString(),
            "Walk-in guest " + name + "; room " + room.getRoomCode());
        return saved;
    }

    public void checkIn(Long id, String adminUsername) {
        Reservation reservation = reservations.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (reservation.getStatus() != ReservationStatus.APPROVED)
            throw new IllegalStateException("Only approved reservations can be checked in.");
        if (LocalDate.now(clock).isBefore(reservation.getCheckInDate()))
            throw new IllegalStateException("The check-in date has not started.");
        BigDecimal requiredDeposit = reservation.getTotalAmount().multiply(new BigDecimal("0.50"))
            .setScale(2, java.math.RoundingMode.HALF_UP);
        if (payments.sumPaidForReservation(id).compareTo(requiredDeposit) < 0)
            throw new IllegalStateException("The 50% down payment must be recorded before check-in.");
        reservation.changeStatus(ReservationStatus.CHECKED_IN);
        audit.record(adminUsername, "GUEST_CHECKED_IN", "RESERVATION", id.toString(),
            "Checked in guest " + reservation.getGuestName());
    }

    public void checkOut(Long id, String adminUsername) {
        Reservation reservation = reservations.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (reservation.getStatus() != ReservationStatus.CHECKED_IN)
            throw new IllegalStateException("Only checked-in guests can be checked out.");
        if (payments.sumPaidForReservation(id).compareTo(reservation.getTotalAmount()) < 0)
            throw new IllegalStateException("The remaining balance must be settled before checkout.");
        reservation.changeStatus(ReservationStatus.CHECKED_OUT);
        audit.record(adminUsername, "GUEST_CHECKED_OUT", "RESERVATION", id.toString(),
            "Checked out guest " + reservation.getGuestName());
    }

    public void markNoShow(Long id, String adminUsername) {
        Reservation reservation = reservations.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (reservation.getStatus() != ReservationStatus.APPROVED
                || !LocalDate.now(clock).isAfter(reservation.getCheckInDate()))
            throw new IllegalStateException("Only an approved reservation whose check-in date has passed can be marked no-show.");
        reservation.changeStatus(ReservationStatus.NO_SHOW);
        audit.record(adminUsername, "RESERVATION_MARKED_NO_SHOW", "RESERVATION", id.toString(),
            "Marked guest " + reservation.getGuestName() + " as no-show.");
    }

    private void validateWalkInDates(LocalDate checkIn, LocalDate checkOut) {
        LocalDate today = LocalDate.now(clock);
        if (checkIn == null || checkOut == null || checkIn.isBefore(today))
            throw new IllegalArgumentException("Walk-in check-in cannot be in the past.");
        if (!checkOut.isAfter(checkIn))
            throw new IllegalArgumentException("Check-out must be after check-in.");
        if (ChronoUnit.DAYS.between(checkIn, checkOut) > 365)
            throw new IllegalArgumentException("A reservation cannot exceed 365 nights.");
    }

    private boolean isUnavailable(Long roomId, LocalDate checkIn, LocalDate checkOut) {
        return isUnavailable(roomId, checkIn, checkOut, null);
    }

    private boolean isUnavailable(Long roomId, LocalDate checkIn, LocalDate checkOut, Long excludeId) {
        return reservations.existsOverlappingReservation(
            roomId, BLOCKING_STATUSES, checkIn, checkOut, excludeId);
    }

    private void validateDates(LocalDate checkIn, LocalDate checkOut) {
        LocalDate today = LocalDate.now(clock);
        if (checkIn == null || checkOut == null || !checkIn.isAfter(today)) {
            throw new IllegalArgumentException("Check-in must be a future date.");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("Check-out must be after check-in.");
        }
        if (ChronoUnit.DAYS.between(checkIn, checkOut) > 365) {
            throw new IllegalArgumentException("A reservation cannot exceed 365 nights.");
        }
    }
}