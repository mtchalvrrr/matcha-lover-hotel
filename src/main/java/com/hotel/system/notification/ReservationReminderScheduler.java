package com.hotel.system.notification;

import com.hotel.system.reservation.Reservation;
import com.hotel.system.reservation.ReservationRepository;
import com.hotel.system.reservation.ReservationSource;
import com.hotel.system.reservation.ReservationStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class ReservationReminderScheduler {
    private final ReservationRepository reservations;
    private final NotificationService notifications;
    private final Clock clock;

    public ReservationReminderScheduler(ReservationRepository reservations,
                                         NotificationService notifications, Clock clock) {
        this.reservations = reservations;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Scheduled(cron = "${hotel.notifications.reminder-cron:0 0 * * * *}",
               zone = "${hotel.time-zone:Asia/Manila}")
    public void sendUpcomingStayReminders() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime horizon = now.plusHours(24);
        LocalDate today = now.toLocalDate();
        LocalDate tomorrow = today.plusDays(1);

        reservations.findByCheckInDateBetweenAndStatusInOrderByCheckInDateAsc(
                        today, tomorrow, List.of(ReservationStatus.APPROVED))
                .stream()
                .filter(reservation -> withinWindow(
                        reservation.getCheckInDate().atTime(reservation.getCheckInTime()), now, horizon))
                .forEach(this::sendCheckInReminder);

        reservations.findByCheckOutDateBetweenAndStatusInOrderByCheckOutDateAsc(
                        today, tomorrow, List.of(ReservationStatus.CHECKED_IN))
                .stream()
                .filter(reservation -> withinWindow(
                        reservation.getCheckOutDate().atTime(reservation.getCheckOutTime()), now, horizon))
                .forEach(this::sendCheckOutReminder);
    }

    private boolean withinWindow(LocalDateTime scheduled, LocalDateTime now, LocalDateTime horizon) {
        return !scheduled.isBefore(now) && !scheduled.isAfter(horizon);
    }

    private void sendCheckInReminder(Reservation reservation) {
        String eventKey = "CHECKIN:" + reservation.getId() + ":" + reservation.getCheckInDate();
        String message = "Reminder: your stay for reservation #" + reservation.getId()
                + " is scheduled to check in on " + reservation.getCheckInDate()
                + " at " + reservation.getCheckInTime() + ".";
        send(reservation, "Upcoming check-in", message, eventKey);
    }

    private void sendCheckOutReminder(Reservation reservation) {
        String eventKey = "CHECKOUT:" + reservation.getId() + ":" + reservation.getCheckOutDate();
        String message = "Reminder: reservation #" + reservation.getId()
                + " is scheduled to check out on " + reservation.getCheckOutDate()
                + " at " + reservation.getCheckOutTime()
                + ". Please settle any remaining balance before checkout.";
        send(reservation, "Upcoming check-out", message, eventKey);
    }

    private void send(Reservation reservation, String title, String message, String eventKey) {
        if (reservation.getSource() == ReservationSource.WALK_IN) {
            notifications.notifyAdminsOnce(title, message, "RESERVATION", eventKey);
        } else {
            notifications.notifyUserOnce(reservation.getCustomerUsername(), title, message,
                    "RESERVATION", eventKey);
        }
    }
}
