package com.mtchlvr.hotel.service;

import com.mtchlvr.hotel.model.*;
import com.mtchlvr.hotel.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class ReservationService {
    private static final LocalTime DEFAULT_CHECK_IN = LocalTime.of(14, 0);
    private static final LocalTime DEFAULT_CHECK_OUT = LocalTime.NOON;
    private static final List<String> NON_OCCUPYING = List.of("CANCELLED", "REJECTED", "VOID");

    private final ReservationRepository reservations;
    private final RoomRepository rooms;
    private final UserRepository users;

    public ReservationService(ReservationRepository reservations, RoomRepository rooms, UserRepository users) {
        this.reservations = reservations;
        this.rooms = rooms;
        this.users = users;
    }

    private LocalDateTime start(Reservation r) {
        LocalTime time = r.getCheckInTime();
        return r.getCheckIn().atTime(time == null ? DEFAULT_CHECK_IN : time);
    }

    private LocalDateTime end(Reservation r) {
        LocalTime time = r.getCheckOutTime();
        return r.getCheckOut().atTime(time == null ? DEFAULT_CHECK_OUT : time);
    }

    private boolean overlaps(LocalDateTime start, LocalDateTime end, Reservation existing) {
        return start.isBefore(this.end(existing)) && end.isAfter(this.start(existing));
    }

    private boolean occupiesInterval(LocalDateTime requestedStart, LocalDateTime requestedEnd,
                                     Reservation existing) {
        if (NON_OCCUPYING.contains(existing.getStatus())) return false;

        if (overlaps(requestedStart, requestedEnd, existing)) return true;

        // An extension request temporarily holds the additional time,
        // even though the original checkout remains unchanged until approval.
        if ("EXTENSION_REQUESTED".equals(existing.getStatus()) &&
                existing.getRequestedCheckOut() != null &&
                existing.getRequestedCheckOutTime() != null) {
            LocalDateTime extensionStart = end(existing);
            LocalDateTime extensionEnd =
                    existing.getRequestedCheckOut().atTime(existing.getRequestedCheckOutTime());

            return requestedStart.isBefore(extensionEnd) &&
                    requestedEnd.isAfter(extensionStart);
        }

        return false;
    }

    /**
     * Void pending reservations when their check-in time arrives.
     * Runs at startup and every 60 seconds using Manila time.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 0)
    @Transactional
    public void voidExpiredPendingReservations() {
        java.time.ZoneId hotelZone = java.time.ZoneId.of("Asia/Manila");
        LocalDateTime now = LocalDateTime.now(hotelZone).withNano(0);

        List<Reservation> expired = reservations.findAll().stream()
            .filter(r -> "PENDING".equals(r.getStatus()))
            .filter(r -> !now.isBefore(start(r)))
            .toList();

        if (!expired.isEmpty()) {
            expired.forEach(r -> r.setStatus("VOID"));
            reservations.saveAll(expired);
        }
    }

    public boolean isAvailable(Room room, LocalDate in, LocalDate out) {
        return isAvailable(room, in, DEFAULT_CHECK_IN, out, DEFAULT_CHECK_OUT);
    }

    public boolean isAvailable(Room room, LocalDate in, LocalTime inTime,
                               LocalDate out, LocalTime outTime) {
        if (room == null || room.getId() == null || in == null || out == null ||
                inTime == null || outTime == null ||
                !room.isAvailable() || !room.isActive()) {
            return false;
        }

        java.time.ZoneId hotelZone = java.time.ZoneId.of("Asia/Manila");
        LocalDateTime now = LocalDateTime.now(hotelZone).withNano(0);
        LocalDateTime requestedStart = in.atTime(inTime);
        LocalDateTime requestedEnd = out.atTime(outTime);
        if (requestedStart.isBefore(now) || !requestedEnd.isAfter(requestedStart)) return false;

        return reservations.findAll().stream().noneMatch(r ->
            r.getRoom() != null && r.getRoom().getId() != null &&
            r.getRoom().getId().equals(room.getId()) &&
            occupiesInterval(requestedStart, requestedEnd, r)
        );
    }

    /**
     * Rechecks an extension against other reservations while excluding
     * the reservation that is requesting the extension.
     */
    public boolean isAvailableForExtension(Room room, Reservation current,
                                           LocalDate newCheckOut, LocalTime newCheckOutTime) {
        if (room == null || room.getId() == null || current == null ||
                newCheckOut == null || newCheckOutTime == null ||
                !room.isAvailable() || !room.isActive()) {
            return false;
        }

        LocalTime currentOut = current.getCheckOutTime() == null
                ? DEFAULT_CHECK_OUT : current.getCheckOutTime();

        LocalDateTime extensionStart = current.getCheckOut().atTime(currentOut);
        LocalDateTime extensionEnd = newCheckOut.atTime(newCheckOutTime);
        LocalDateTime now = LocalDateTime.now(
                java.time.ZoneId.of("Asia/Manila")).withNano(0);

        // The extension can be requested after the original checkout time,
        // but its new checkout must still be in the future.
        if (!extensionEnd.isAfter(extensionStart) || !extensionEnd.isAfter(now)) {
            return false;
        }

        return reservations.findAll().stream()
                .filter(r -> !java.util.Objects.equals(r.getId(), current.getId()))
                .filter(r -> r.getRoom() != null && r.getRoom().getId() != null)
                .filter(r -> r.getRoom().getId().equals(room.getId()))
                .noneMatch(r -> occupiesInterval(extensionStart, extensionEnd, r));
    }

    @Transactional
    public Reservation updateStatus(Long reservationId, String status) {
        if (status == null || !List.of("APPROVED", "REJECTED", "CANCELLED").contains(status)) {
            throw new IllegalArgumentException("Choose a valid reservation status.");
        }

        Reservation reservation = reservations.findById(reservationId)
            .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));

        String currentStatus = reservation.getStatus();
        String nextStatus = status;

        if ("VOID".equals(currentStatus)) {
            throw new IllegalArgumentException(
                "This reservation has expired. The customer must submit a new reservation request."
            );
        }

        if ("CANCELLATION_REQUESTED".equals(currentStatus)) {
            if ("CANCELLED".equals(status)) {
                nextStatus = "CANCELLED";
            } else if ("APPROVED".equals(status)) {
                String previousStatus = reservation.getStatusBeforeCancellation();

                // Older records have no saved prior status; preserve their
                // previous behavior by treating them as approved bookings.
                nextStatus = ("PENDING".equals(previousStatus) ||
                              "APPROVED".equals(previousStatus))
                        ? previousStatus : "APPROVED";
            } else {
                throw new IllegalArgumentException(
                    "For a cancellation request, choose keep reservation or confirm cancellation."
                );
            }
        }

        if ("APPROVED".equals(nextStatus)) {
            if ("PENDING".equals(currentStatus)) {
                LocalDateTime now = LocalDateTime.now(
                    java.time.ZoneId.of("Asia/Manila")
                ).withNano(0);

                if (!now.isBefore(start(reservation))) {
                    throw new IllegalArgumentException(
                        "This reservation has expired because its check-in time has arrived."
                    );
                }
            }

            Room room = reservation.getRoom();
            LocalDateTime requestedStart = start(reservation);
            LocalDateTime requestedEnd = end(reservation);

            boolean conflict = reservations.findAll().stream().anyMatch(other ->
                other.getId() != null && !other.getId().equals(reservation.getId()) &&
                other.getRoom() != null && other.getRoom().getId() != null &&
                other.getRoom().getId().equals(room.getId()) &&
                occupiesInterval(requestedStart, requestedEnd, other)
            );

            if (conflict) {
                throw new IllegalArgumentException(
                    "Cannot approve: this room overlaps another active reservation."
                );
            }
        }

        reservation.setStatus(nextStatus);
        if ("CANCELLATION_REQUESTED".equals(currentStatus)) {
            reservation.setStatusBeforeCancellation(null);
        }
        return reservations.save(reservation);
    }

    // Compatibility overload for existing callers and tests.
    @Transactional
    public Reservation reserve(String email, Long roomId, LocalDate in, LocalDate out) {
        if (in == null || out == null || !out.isAfter(in) || in.isBefore(LocalDate.now(java.time.ZoneId.of("Asia/Manila")))) {
            throw new IllegalArgumentException(
                "Choose a valid check-in date and a check-out date after it."
            );
        }
        return reserve(email, roomId, in, DEFAULT_CHECK_IN, out, DEFAULT_CHECK_OUT);
    }

    @Transactional
    public Reservation reserve(String email, Long roomId, LocalDate in, LocalTime inTime,
                               LocalDate out, LocalTime outTime) {
        java.time.ZoneId hotelZone = java.time.ZoneId.of("Asia/Manila");
        LocalDateTime now = LocalDateTime.now(hotelZone).withNano(0);
        if (in == null || out == null || inTime == null || outTime == null ||
                in.atTime(inTime).isBefore(now) ||
                !out.atTime(outTime).isAfter(in.atTime(inTime))) {
            throw new IllegalArgumentException(
                "Choose a future check-in date/time and a checkout after check-in."
            );
        }

        AppUser user = users.findByEmailIgnoreCase(email).orElseThrow();
        Room room = rooms.findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Room not found."));

        if (!isAvailable(room, in, inTime, out, outTime)) {
            throw new IllegalArgumentException(
                "That room is not available for the selected dates and times."
            );
        }

        return reservations.save(new Reservation(user, room, in, out, inTime, outTime));
    }
}
