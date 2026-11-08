package com.hotel.system.reservation;

import com.hotel.system.account.SystemUser;
import com.hotel.system.account.SystemUserRepository;
import com.hotel.system.audit.AuditService;
import com.hotel.system.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional
public class ReservationExtensionService {
    private static final List<ReservationStatus> BLOCKING =
            List.of(ReservationStatus.PENDING, ReservationStatus.APPROVED, ReservationStatus.CHECKED_IN);

    private final ReservationExtensionRequestRepository requests;
    private final ReservationRepository reservations;
    private final SystemUserRepository users;
    private final AuditService audit;
    private final NotificationService notifications;
    private final Clock clock;

    public ReservationExtensionService(ReservationExtensionRequestRepository requests,
            ReservationRepository reservations, SystemUserRepository users, AuditService audit,
            NotificationService notifications, Clock clock) {
        this.requests = requests;
        this.reservations = reservations;
        this.users = users;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
    }

    public ReservationExtensionRequest request(Long reservationId, String username,
                                                LocalDate requestedCheckout, String reason) {
        SystemUser customer = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (!java.util.Objects.equals(customer.getId(), reservation.getCustomerId()))
            throw new IllegalArgumentException("Reservation not found.");
        if (reservation.getStatus() != ReservationStatus.APPROVED
                && reservation.getStatus() != ReservationStatus.CHECKED_IN)
            throw new IllegalStateException("Only approved or currently checked-in reservations can be extended.");
        if (requestedCheckout == null || !requestedCheckout.isAfter(reservation.getCheckOutDate()))
            throw new IllegalArgumentException("The requested checkout date must be later than the current checkout date.");
        if (ChronoUnit.DAYS.between(reservation.getCheckInDate(), requestedCheckout) > 365)
            throw new IllegalArgumentException("A reservation cannot exceed 365 nights.");
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.length() > 500)
            throw new IllegalArgumentException("The reason must be 500 characters or fewer.");
        if (requests.existsByReservationIdAndStatus(reservationId, ExtensionRequestStatus.PENDING))
            throw new IllegalStateException("An extension request is already awaiting review.");
        ensureAvailable(reservation, requestedCheckout);
        ReservationExtensionRequest request = requests.save(new ReservationExtensionRequest(
                reservationId, username, reservation.getCheckOutDate(), requestedCheckout, cleanReason));
        audit.record(username, "RESERVATION_EXTENSION_REQUESTED", "RESERVATION", reservationId.toString(),
                "Requested checkout change from " + reservation.getCheckOutDate() + " to " + requestedCheckout);
        notifications.notifyAdmins("Reservation extension requested",
                username + " requested an extension for reservation #" + reservationId + ".", "RESERVATION");
        return request;
    }

    public void review(Long requestId, boolean approve, String reviewer, String reason) {
        ReservationExtensionRequest request = requests.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Extension request not found."));
        if (request.getStatus() != ExtensionRequestStatus.PENDING)
            throw new IllegalStateException("This extension request has already been reviewed.");
        Reservation reservation = reservations.findById(request.getReservationId())
                .orElseThrow(() -> new IllegalStateException("The linked reservation no longer exists."));
        String cleanReason = reason == null ? "" : reason.trim();
        if (!approve && cleanReason.isBlank())
            throw new IllegalArgumentException("A reason is required when rejecting an extension.");
        if (cleanReason.length() > 500)
            throw new IllegalArgumentException("The review reason must be 500 characters or fewer.");
        if (approve) {
            if (reservation.getStatus() != ReservationStatus.APPROVED
                    && reservation.getStatus() != ReservationStatus.CHECKED_IN)
                throw new IllegalStateException("This reservation is no longer eligible for an extension.");
            if (!reservation.getCheckOutDate().equals(request.getPreviousCheckoutDate()))
                throw new IllegalStateException("The reservation checkout date changed after this request was submitted.");
            ensureAvailable(reservation, request.getRequestedCheckoutDate());
            reservation.extendCheckout(request.getRequestedCheckoutDate());
        }
        request.review(approve, reviewer, cleanReason, Instant.now(clock));
        audit.record(reviewer, approve ? "RESERVATION_EXTENSION_APPROVED" : "RESERVATION_EXTENSION_REJECTED",
                "RESERVATION", reservation.getId().toString(),
                "Extension request #" + requestId + (approve
                        ? " approved; new checkout " + request.getRequestedCheckoutDate()
                        : " rejected; reason: " + cleanReason));
        notifications.notifyUser(request.getCustomerUsername(),
                approve ? "Reservation extension approved" : "Reservation extension rejected",
                approve ? "Your reservation #" + reservation.getId() + " now checks out on "
                        + request.getRequestedCheckoutDate() + ". Any additional balance is shown in your payment page."
                        : "Your extension request for reservation #" + reservation.getId()
                        + " was rejected. " + cleanReason, "RESERVATION");
    }

    @Transactional(readOnly = true)
    public List<ReservationExtensionRequest> customerRequests(String username) {
        return requests.findByCustomerUsernameOrderByRequestedAtDesc(username);
    }

    @Transactional(readOnly = true)
    public List<ReservationExtensionRequest> requestsForReservation(Long reservationId, String username) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (!reservation.getCustomerUsername().equalsIgnoreCase(username))
            throw new IllegalArgumentException("Reservation not found.");
        return requests.findByReservationIdOrderByRequestedAtDesc(reservationId);
    }

    @Transactional(readOnly = true)
    public List<ReservationExtensionRequest> allRequests() {
        return requests.findAllByOrderByRequestedAtDesc();
    }

    private void ensureAvailable(Reservation reservation, LocalDate requestedCheckout) {
        boolean conflict = reservations.existsOverlappingReservation(reservation.getRoomId(), BLOCKING,
                reservation.getCheckOutDate(), requestedCheckout, reservation.getId());
        if (conflict)
            throw new IllegalStateException("The room is already reserved during the requested extension dates.");
    }
}
