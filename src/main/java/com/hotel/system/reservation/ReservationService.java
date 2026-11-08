package com.hotel.system.reservation;

import com.hotel.system.account.SystemUser;
import com.hotel.system.account.SystemUserRepository;
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

    public ReservationService(ReservationRepository reservations, RoomRepository rooms,
                              SystemUserRepository users, Clock clock) {
        this.reservations = reservations;
        this.rooms = rooms;
        this.users = users;
        this.clock = clock;
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
        return reservations.save(new Reservation(customer.getId(), customer.getUsername(),
            customer.getEmail(), room.getId(), room.getRoomCode(), room.getName(),
            checkIn, checkOut, guests, nights, room.getNightlyRate(), total));
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

    public void review(Long id, boolean approve) {
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
        } else {
            reservation.changeStatus(ReservationStatus.REJECTED);
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
    }

    private boolean isUnavailable(Long roomId, LocalDate checkIn, LocalDate checkOut) {
        return isUnavailable(roomId, checkIn, checkOut, null);
    }

    private boolean isUnavailable(Long roomId, LocalDate checkIn, LocalDate checkOut, Long excludeId) {
        List<Reservation> candidates = reservations.findAll();
        return candidates.stream().anyMatch(r -> !r.getId().equals(excludeId)
            && r.getRoomId().equals(roomId)
            && BLOCKING_STATUSES.contains(r.getStatus())
            && r.getCheckInDate().isBefore(checkOut)
            && r.getCheckOutDate().isAfter(checkIn));
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