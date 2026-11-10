package com.hotel.system.reservation;

import com.hotel.system.account.SystemUser;
import com.hotel.system.account.SystemUserRepository;
import com.hotel.system.audit.AuditService;
import com.hotel.system.notification.NotificationService;
import com.hotel.system.payment.Payment;
import com.hotel.system.payment.PaymentRepository;
import com.hotel.system.payment.PaymentStatus;
import com.hotel.system.room.Room;
import com.hotel.system.room.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class ReservationExtensionService {
    private static final List<ReservationStatus> BLOCKING_STATUSES = List.of(
            ReservationStatus.PAYMENT_PENDING, ReservationStatus.PENDING,
            ReservationStatus.APPROVED, ReservationStatus.CHECKED_IN);

    private final ReservationExtensionRequestRepository extensions;
    private final ReservationRepository reservations;
    private final RoomRepository rooms;
    private final SystemUserRepository users;
    private final PaymentRepository payments;
    private final AuditService audit;
    private final NotificationService notifications;
    private final Clock clock;

    public ReservationExtensionService(ReservationExtensionRequestRepository extensions,
            ReservationRepository reservations, RoomRepository rooms, SystemUserRepository users,
            PaymentRepository payments, AuditService audit, NotificationService notifications, Clock clock) {
        this.extensions = extensions;
        this.reservations = reservations;
        this.rooms = rooms;
        this.users = users;
        this.payments = payments;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
    }

    public ReservationExtensionRequest request(Long reservationId, String username,
            LocalDate requestedCheckout, String customerNote) {
        SystemUser customer = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (!Objects.equals(customer.getId(), reservation.getCustomerId()))
            throw new IllegalArgumentException("Reservation not found.");
        if (reservation.getStatus() != ReservationStatus.APPROVED
                && reservation.getStatus() != ReservationStatus.CHECKED_IN)
            throw new IllegalStateException("Only approved or checked-in reservations can request an extension.");
        if (reservation.getCheckedOutAt() != null)
            throw new IllegalStateException("A completed stay cannot be extended.");
        if (requestedCheckout == null || !requestedCheckout.isAfter(reservation.getCheckOutDate()))
            throw new IllegalArgumentException("Choose a checkout date later than the current checkout date.");
        long additionalNights = ChronoUnit.DAYS.between(reservation.getCheckOutDate(), requestedCheckout);
        long totalNights = ChronoUnit.DAYS.between(reservation.getCheckInDate(), requestedCheckout);
        if (additionalNights < 1 || totalNights > 365)
            throw new IllegalArgumentException("An extension must add at least one night and the total stay cannot exceed 365 nights.");
        if (extensions.findFirstByReservationIdAndStatusInOrderByCreatedAtDesc(reservationId,
                List.of(ReservationExtensionRequest.Status.PENDING,
                        ReservationExtensionRequest.Status.APPROVED_AWAITING_PAYMENT)).isPresent())
            throw new IllegalStateException("This reservation already has an extension awaiting review or payment.");
        validateAvailability(reservation, requestedCheckout);
        String note = customerNote == null ? "" : customerNote.trim();
        if (note.length() > 500) throw new IllegalArgumentException("The note must be 500 characters or fewer.");
        BigDecimal rate = reservation.getNightlyRate().setScale(2, RoundingMode.HALF_UP);
        BigDecimal amount = rate.multiply(BigDecimal.valueOf(additionalNights)).setScale(2, RoundingMode.HALF_UP);
        ReservationExtensionRequest saved = extensions.save(new ReservationExtensionRequest(
                reservationId, customer.getUsername(), reservation.getCheckOutDate(),
                requestedCheckout, Math.toIntExact(additionalNights), rate, amount, note));
        audit.record(username, "RESERVATION_EXTENSION_REQUESTED", "RESERVATION_EXTENSION",
                saved.getId().toString(), "Reservation #" + reservationId + " requested checkout extension to "
                        + requestedCheckout + "; additional charge PHP " + amount + ".");
        notifications.notifyAdmins("Reservation extension awaiting review",
                "Customer " + username + " requested an extension for reservation #" + reservationId
                        + " to " + requestedCheckout + ".", "RESERVATION");
        return saved;
    }

    public void review(Long extensionId, boolean approve, String admin, String reviewNote) {
        ReservationExtensionRequest extension = extensions.findById(extensionId)
                .orElseThrow(() -> new IllegalArgumentException("Extension request not found."));
        if (extension.getStatus() != ReservationExtensionRequest.Status.PENDING)
            throw new IllegalStateException("This extension request has already been reviewed.");
        String note = reviewNote == null ? "" : reviewNote.trim();
        if (!approve && note.isBlank())
            throw new IllegalArgumentException("Enter a reason when rejecting an extension request.");
        if (note.length() > 500) throw new IllegalArgumentException("The review note must be 500 characters or fewer.");
        Reservation reservation = reservations.findById(extension.getReservationId())
                .orElseThrow(() -> new IllegalStateException("The linked reservation no longer exists."));
        if (approve) {
            if (reservation.getStatus() != ReservationStatus.APPROVED
                    && reservation.getStatus() != ReservationStatus.CHECKED_IN)
                throw new IllegalStateException("The reservation is no longer eligible for an extension.");
            if (!reservation.getCheckOutDate().equals(extension.getCurrentCheckoutDate()))
                throw new IllegalStateException("The reservation checkout date changed after this request was submitted.");
            validateAvailability(reservation, extension.getRequestedCheckoutDate());
            extension.approve(admin, note, Instant.now(clock));
            audit.record(admin, "RESERVATION_EXTENSION_APPROVED", "RESERVATION_EXTENSION",
                    extensionId.toString(), "Approved extension for reservation #" + reservation.getId()
                            + ". Customer must pay the full additional charge of PHP "
                            + extension.getExtensionAmount() + ".");
            notifications.notifyUser(extension.getCustomerUsername(), "Extension approved",
                    "Your extension for reservation #" + reservation.getId()
                            + " was approved. Pay the full additional charge of PHP "
                            + extension.getExtensionAmount() + " to confirm the new checkout date.",
                    "RESERVATION");
        } else {
            extension.reject(admin, note, Instant.now(clock));
            audit.record(admin, "RESERVATION_EXTENSION_REJECTED", "RESERVATION_EXTENSION",
                    extensionId.toString(), "Rejected extension for reservation #" + reservation.getId()
                            + ". Reason: " + note);
            notifications.notifyUser(extension.getCustomerUsername(), "Extension request rejected",
                    "Your extension request for reservation #" + reservation.getId()
                            + " was rejected. Reason: " + note, "RESERVATION");
        }
    }

    @Transactional(readOnly = true)
    public ReservationExtensionRequest customerRequest(Long id, String username) {
        ReservationExtensionRequest extension = extensions.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Extension request not found."));
        if (!extension.getCustomerUsername().equalsIgnoreCase(username))
            throw new IllegalArgumentException("Extension request not found.");
        return extension;
    }

    @Transactional(readOnly = true)
    public List<ReservationExtensionRequest> customerRequests(String username) {
        return extensions.findByCustomerUsernameOrderByCreatedAtDesc(username);
    }

    @Transactional(readOnly = true)
    public List<ReservationExtensionRequest> allRequests() {
        return extensions.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<ReservationExtensionRequest> pendingRequests() {
        return extensions.findByStatusOrderByCreatedAtAsc(ReservationExtensionRequest.Status.PENDING);
    }

    @Transactional(readOnly = true)
    public ReservationExtensionRequest approvedRequestForPayment(Long id, String username) {
        ReservationExtensionRequest extension = customerRequest(id, username);
        if (extension.getStatus() != ReservationExtensionRequest.Status.APPROVED_AWAITING_PAYMENT)
            throw new IllegalStateException("Only an approved extension awaiting payment can be paid.");
        return extension;
    }

    public void attachPayment(Long extensionId, Long paymentId, String username) {
        ReservationExtensionRequest extension = approvedRequestForPayment(extensionId, username);
        extension.attachPayment(paymentId);
    }

    public void completePayment(Long extensionId, Long paymentId, BigDecimal amount) {
        ReservationExtensionRequest extension = extensions.findById(extensionId)
                .orElseThrow(() -> new IllegalArgumentException("Extension request not found."));
        if (extension.getStatus() == ReservationExtensionRequest.Status.PAID) return;
        if (!Objects.equals(extension.getPaymentId(), paymentId)
                || extension.getStatus() != ReservationExtensionRequest.Status.APPROVED_AWAITING_PAYMENT
                || amount == null || amount.compareTo(extension.getExtensionAmount()) != 0)
            throw new IllegalStateException("The confirmed payment does not match the approved extension charge.");
        Reservation reservation = reservations.findById(extension.getReservationId())
                .orElseThrow(() -> new IllegalStateException("The linked reservation no longer exists."));
        if (!reservation.getCheckOutDate().equals(extension.getCurrentCheckoutDate()))
            throw new IllegalStateException("The reservation checkout date changed before extension payment completed.");
        validateAvailability(reservation, extension.getRequestedCheckoutDate());
        extension.markPaid(Instant.now(clock));
        reservation.extendCheckout(extension.getRequestedCheckoutDate());
        reservations.save(reservation);
        audit.record("XENDIT_WEBHOOK", "RESERVATION_EXTENSION_PAID", "RESERVATION_EXTENSION",
                extensionId.toString(), "Payment #" + paymentId + " confirmed. Reservation #"
                        + reservation.getId() + " checkout extended to " + reservation.getCheckOutDate()
                        + "; new reservation total PHP " + reservation.getTotalAmount() + ".");
        notifications.notifyUser(extension.getCustomerUsername(), "Reservation extension confirmed",
                "Your extension payment was confirmed. Reservation #" + reservation.getId()
                        + " now has a checkout date of " + reservation.getCheckOutDate() + ".",
                "RESERVATION");
        notifications.notifyAdmins("Reservation extension payment confirmed",
                "Extension #" + extensionId + " for reservation #" + reservation.getId()
                        + " was paid and the checkout date was updated.", "RESERVATION");
    }

    /** Allows a new checkout attempt only after the previously linked payment definitively failed. */
    public void replaceFailedPayment(Long extensionId, Long previousPaymentId, Long newPaymentId,
                                     String username) {
        ReservationExtensionRequest extension = approvedRequestForPayment(extensionId, username);
        if (!Objects.equals(extension.getPaymentId(), previousPaymentId))
            throw new IllegalStateException("The extension payment changed; refresh and try again.");
        Payment previous = payments.findById(previousPaymentId)
                .orElseThrow(() -> new IllegalStateException("The previous extension payment was not found."));
        if (previous.getStatus() != PaymentStatus.FAILED && previous.getStatus() != PaymentStatus.VOIDED)
            throw new IllegalStateException("A new checkout can be created only after the previous payment has failed or expired.");
        extension.replacePayment(newPaymentId);
    }

    private void validateAvailability(Reservation reservation, LocalDate requestedCheckout) {
        Room room = rooms.findByIdAndActiveTrue(reservation.getRoomId())
                .orElseThrow(() -> new IllegalStateException("The reserved room is no longer active."));
        if (!room.isBookable())
            throw new IllegalStateException("The room is not currently bookable.");
        if (reservations.existsOverlappingReservation(reservation.getRoomId(), BLOCKING_STATUSES,
                reservation.getCheckOutDate(), requestedCheckout, reservation.getId()))
            throw new IllegalStateException("The room is already reserved during part of the requested extension.");
    }
}