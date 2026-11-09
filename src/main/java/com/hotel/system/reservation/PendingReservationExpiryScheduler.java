package com.hotel.system.reservation;

import com.hotel.system.audit.AuditService;
import com.hotel.system.notification.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class PendingReservationExpiryScheduler {
    private final ReservationRepository reservations;
    private final AuditService audit;
    private final NotificationService notifications;
    private final Clock clock;
    private final long expiryHours;

    public PendingReservationExpiryScheduler(
            ReservationRepository reservations,
            AuditService audit,
            NotificationService notifications,
            Clock clock,
            @Value("${hotel.reservations.pending-expiration-hours:24}") long expiryHours) {
        if (expiryHours < 1 || expiryHours > 720) {
            throw new IllegalArgumentException("Pending reservation expiry must be between 1 and 720 hours.");
        }
        this.reservations = reservations;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
        this.expiryHours = expiryHours;
    }

    @Scheduled(fixedDelayString = "${hotel.reservations.expiry-check-ms:300000}")
    @Transactional
    public void expireStalePendingReservations() {
        Instant now = Instant.now(clock);
        java.time.LocalDateTime localNow = java.time.LocalDateTime.now(clock);
        Instant cutoff = now.minus(Duration.ofHours(expiryHours));

        // Check the actual check-in deadline first. A paid reservation that is still
        // PENDING when its scheduled check-in time arrives is VOID, not merely stale.
        List<Reservation> dueForCheckIn = reservations
                .findByStatusAndCheckInDateLessThanEqualOrderByCheckInDateAsc(
                        ReservationStatus.PENDING, localNow.toLocalDate());
        for (Reservation reservation : dueForCheckIn) {
            if (reservation.getStatus() != ReservationStatus.PENDING) continue;
            java.time.LocalDateTime checkInAt = reservation.getCheckInDate()
                    .atTime(reservation.getCheckInTime());
            if (!localNow.isBefore(checkInAt)) {
                reservation.voidAtCheckIn(now);
                String id = reservation.getId().toString();
                audit.record("SYSTEM", "RESERVATION_VOIDED_AT_CHECK_IN", "RESERVATION", id,
                        "Reservation became VOID because scheduled check-in arrived before Admin review.");
                if (reservation.getSource() == ReservationSource.ONLINE) {
                    notifications.notifyUser(reservation.getCustomerUsername(), "Reservation marked VOID",
                            "Reservation #" + id + " was marked VOID because its check-in time arrived before the hotel Admin reviewed it.",
                            "RESERVATION");
                }
            }
        }

        // Requests that have been pending too long may expire before check-in.
        List<Reservation> stale = reservations.findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                ReservationStatus.PENDING, cutoff);
        for (Reservation reservation : stale) {
            if (reservation.getStatus() != ReservationStatus.PENDING) continue;
            java.time.LocalDateTime checkInAt = reservation.getCheckInDate()
                    .atTime(reservation.getCheckInTime());
            if (!localNow.isBefore(checkInAt)) continue; // handled as VOID above
            reservation.expire(now);
            String id = reservation.getId().toString();
            audit.record("SYSTEM", "RESERVATION_EXPIRED", "RESERVATION", id,
                    "Pending reservation expired after " + expiryHours + " hours without approval.");
            if (reservation.getSource() == ReservationSource.ONLINE) {
                notifications.notifyUser(reservation.getCustomerUsername(), "Reservation request expired",
                        "Reservation #" + id + " expired because it was not reviewed within "
                                + expiryHours + " hours. You may submit a new request.",
                        "RESERVATION");
            }
        }
    }
}
