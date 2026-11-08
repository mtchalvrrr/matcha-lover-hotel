package com.hotel.system.reservation;

import com.hotel.system.account.SystemUser;
import com.hotel.system.account.SystemUserRepository;
import com.hotel.system.audit.AuditService;
import com.hotel.system.notification.NotificationService;
import com.hotel.system.payment.PaymentRepository;
import com.hotel.system.payment.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final ReservationExtensionRequestRepository extensions;
    private final ReservationRepository reservations;
    private final SystemUserRepository users;
    private final PaymentRepository payments;
    private final AuditService audit;
    private final NotificationService notifications;
    private final Clock clock;

    public ReservationExtensionService(ReservationExtensionRequestRepository extensions,
            ReservationRepository reservations, SystemUserRepository users,
            PaymentRepository payments, AuditService audit,
            NotificationService notifications, Clock clock) {
        this.extensions = extensions;
        this.reservations = reservations;
        this.users = users;
        this.payments = payments;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
    }

    public ReservationExtensionRequest request(Long reservationId, String username, LocalDate newCheckout) {
        SystemUser customer = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        Reservation reservation = ownedReservation(reservationId, customer.getUsername());
        if (reservation.getSource() != ReservationSource.ONLINE
                || (reservation.getStatus() != ReservationStatus.APPROVED
                    && reservation.getStatus() != ReservationStatus.CHECKED_IN)) {
            throw new IllegalStateException("Only approved or currently checked-in online reservations can be extended.");
        }
        LocalDate today = LocalDate.now(clock);
        if (newCheckout == null || !newCheckout.isAfter(reservation.getCheckOutDate())
                || !newCheckout.isAfter(today)) {
            throw new IllegalArgumentException("The new checkout date must be later than the current checkout date and today.");
        }
        long extraNights = ChronoUnit.DAYS.between(reservation.getCheckOutDate(), newCheckout);
        if (extraNights > 30) throw new IllegalArgumentException("An extension request cannot exceed 30 additional nights.");
        if (extensions.existsByReservationIdAndStatus(reservationId, ExtensionRequestStatus.PENDING)) {
            throw new IllegalStateException("An extension request is already awaiting review.");
        }
        if (reservations.existsOverlappingReservation(reservation.getRoomId(), BLOCKING,
                reservation.getCheckInDate(), newCheckout, reservationId)) {
            throw new IllegalStateException("The room is not available for the requested extension dates.");
        }
        BigDecimal amount = reservation.getNightlyRate().multiply(BigDecimal.valueOf(extraNights))
                .setScale(2, RoundingMode.HALF_UP);
        ReservationExtensionRequest request = extensions.save(new ReservationExtensionRequest(
                reservationId, customer.getUsername(), reservation.getCheckOutDate(),
                newCheckout, Math.toIntExact(extraNights), amount));
        audit.record(username, "RESERVATION_EXTENSION_REQUESTED", "RESERVATION", reservationId.toString(),
                "Extension request #" + request.getId() + "; checkout " + reservation.getCheckOutDate()
                        + " to " + newCheckout + "; additional amount " + amount);
        notifications.notifyAdmins("Reservation extension requested",
                "Customer " + username + " requested an extension for reservation #" + reservationId + ".",
                "RESERVATION");
        return request;
    }

    public void review(Long requestId, boolean approve, String adminUsername, String reason) {
        ReservationExtensionRequest request = extensions.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Extension request not found."));
        if (request.getStatus() != ExtensionRequestStatus.PENDING)
            throw new IllegalStateException("This extension request has already been reviewed.");
        if (reason != null && reason.trim().length() > 1000)
            throw new IllegalArgumentException("The review reason must be 1,000 characters or fewer.");
        Reservation reservation = reservations.findById(request.getReservationId())
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (approve) {
            if (reservation.getStatus() != ReservationStatus.APPROVED
                    && reservation.getStatus() != ReservationStatus.CHECKED_IN)
                throw new IllegalStateException("This reservation is no longer eligible for an extension.");
            if (!reservation.getCheckOutDate().equals(request.getOriginalCheckOut()))
                throw new IllegalStateException("The reservation dates changed after this request was submitted. Submit a new request.");
            if (!request.getRequestedCheckOut().isAfter(LocalDate.now(clock)))
                throw new IllegalStateException("The requested checkout date has already passed.");
            if (reservations.existsOverlappingReservation(reservation.getRoomId(), BLOCKING,
                    reservation.getCheckInDate(), request.getRequestedCheckOut(), reservation.getId()))
                throw new IllegalStateException("The room is no longer available for the requested extension dates.");
            BigDecimal newTotal = reservation.getTotalAmount().add(request.getAdditionalAmount())
                    .setScale(2, RoundingMode.HALF_UP);
            reservation.extendStay(request.getRequestedCheckOut(),
                    Math.addExact(reservation.getNights(), request.getExtraNights()), newTotal);
        }
        request.review(approve, adminUsername, reason, Instant.now(clock));
        audit.record(adminUsername, approve ? "RESERVATION_EXTENSION_APPROVED" : "RESERVATION_EXTENSION_REJECTED",
                "RESERVATION", reservation.getId().toString(),
                "Extension request #" + requestId + (approve
                        ? " approved; new checkout " + request.getRequestedCheckOut()
                            + "; additional amount " + request.getAdditionalAmount()
                        : " rejected. Reason: " + (reason == null ? "" : reason.trim())));
        notifications.notifyUser(request.getCustomerUsername(),
                approve ? "Reservation extension approved" : "Reservation extension rejected",
                approve ? "Your reservation #" + reservation.getId() + " is extended to "
                        + request.getRequestedCheckOut() + ". Additional charge: ₱"
                        + request.getAdditionalAmount() + ". Please settle any updated balance before checkout."
                        : "Your extension request for reservation #" + reservation.getId()
                        + " was rejected. Reason: " + (reason == null ? "" : reason.trim()),
                "RESERVATION");
    }

    @Transactional(readOnly = true)
    public Reservation customerReservation(Long id, String username) {
        Reservation reservation = ownedReservation(id, username);
        if (reservation.getSource() != ReservationSource.ONLINE
                || (reservation.getStatus() != ReservationStatus.APPROVED
                    && reservation.getStatus() != ReservationStatus.CHECKED_IN)) {
            throw new IllegalStateException("Only approved or currently checked-in online reservations can be extended.");
        }
        return reservation;
    }

    @Transactional(readOnly = true)
    public List<ReservationExtensionRequest> customerRequests(String username) {
        return extensions.findByCustomerUsernameOrderByCreatedAtDesc(username);
    }

    @Transactional(readOnly = true)
    public List<ReservationExtensionRequest> allRequests() {
        return extensions.findAllByOrderByCreatedAtDesc();
    }

    private Reservation ownedReservation(Long id, String username) {
        Reservation reservation = reservations.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (!reservation.getCustomerUsername().equalsIgnoreCase(username))
            throw new IllegalArgumentException("Reservation not found.");
        return reservation;
    }
}
