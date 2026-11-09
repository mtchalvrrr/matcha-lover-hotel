package com.hotel.system.reservation;

import com.hotel.system.account.SystemUser;
import com.hotel.system.account.SystemUserRepository;
import com.hotel.system.config.HotelStayTimePolicy;
import com.hotel.system.audit.AuditService;
import com.hotel.system.payment.PaymentRepository;
import com.hotel.system.payment.PaymentStatus;
import com.hotel.system.payment.PaymentRefundRequest;
import com.hotel.system.payment.PaymentRefundRequestRepository;
import com.hotel.system.payment.RefundRequestStatus;
import com.hotel.system.notification.NotificationService;
import com.hotel.system.room.Room;
import com.hotel.system.room.RoomNotFoundException;
import com.hotel.system.room.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional
public class ReservationService {
    private static final List<ReservationStatus> BLOCKING_STATUSES =
        List.of(ReservationStatus.PAYMENT_PENDING, ReservationStatus.PENDING, ReservationStatus.APPROVED, ReservationStatus.CHECKED_IN);

    private final ReservationRepository reservations;
    private final RoomRepository rooms;
    private final SystemUserRepository users;
    private final Clock clock;
    private final AuditService audit;
    private final PaymentRepository payments;
    private final PaymentRefundRequestRepository refundRequests;
    private final NotificationService notifications;
    private final HotelStayTimePolicy stayTimes;

    public ReservationService(ReservationRepository reservations, RoomRepository rooms,
                              SystemUserRepository users, Clock clock, AuditService audit, PaymentRepository payments,
                              PaymentRefundRequestRepository refundRequests, NotificationService notifications, HotelStayTimePolicy stayTimes) {
        this.reservations = reservations;
        this.rooms = rooms;
        this.users = users;
        this.clock = clock;
        this.audit = audit;
        this.payments = payments;
        this.refundRequests = refundRequests;
        this.notifications = notifications;
        this.stayTimes = stayTimes;
    }

    public Reservation request(String username, Long roomId, LocalDate checkIn,
                               LocalDate checkOut, Integer guests) {
        SystemUser customer = users.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new IllegalArgumentException("Customer account was not found."));
        Room room = rooms.findByIdAndActiveTrue(roomId)
            .orElseThrow(() -> new RoomNotFoundException("This room is not available."));
        if (!room.isBookable()) {
            throw new IllegalStateException("This room is not ready for guests or is under maintenance.");
        }
        validateDates(checkIn, checkOut);
        if (guests == null || guests < 1 || guests > room.getCapacity()) {
            throw new IllegalArgumentException("Guest count must be between 1 and the room capacity.");
        }
        if (isUnavailable(roomId, checkIn, checkOut)) {
            throw new IllegalArgumentException("This room is already reserved for some or all of those dates.");
        }
        int nights = Math.toIntExact(ChronoUnit.DAYS.between(checkIn, checkOut));
        BigDecimal total = room.getNightlyRate().multiply(BigDecimal.valueOf(nights));
        room.touchAvailability();
        Reservation reservation = new Reservation(customer.getId(), customer.getUsername(),
            customer.getEmail(), room.getId(), room.getRoomCode(), room.getName(),
            checkIn, checkOut, guests, nights, room.getNightlyRate(), total);
        reservation.setStayTimes(stayTimes.checkInTime(), stayTimes.checkOutTime());
        reservation.changeStatus(ReservationStatus.PAYMENT_PENDING);
        Reservation saved = reservations.save(reservation);
        audit.record(username, "RESERVATION_CHECKOUT_STARTED", "RESERVATION", saved.getId().toString(),
            "Room " + room.getRoomCode() + ", stay " + checkIn + " to " + checkOut
                    + ". The reservation will be submitted for manager review only after full payment is verified.");
        return saved;
    }


    @Transactional(readOnly = true)
    public Reservation customerReservationForModification(Long reservationId, String username) {
        SystemUser customer = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (!java.util.Objects.equals(reservation.getCustomerId(), customer.getId())) {
            throw new IllegalArgumentException("Reservation not found.");
        }
        ensureModifiable(reservation);
        return reservation;
    }

    public Reservation modifyCustomerReservation(Long reservationId, String username, Long roomId,
                                                  LocalDate checkIn, LocalDate checkOut, Integer guests) {
        SystemUser customer = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (!java.util.Objects.equals(reservation.getCustomerId(), customer.getId())) {
            throw new IllegalArgumentException("Reservation not found.");
        }
        ensureModifiable(reservation);
        if (payments.sumPaidForReservation(reservationId, PaymentStatus.PAID).signum() > 0) {
            throw new IllegalStateException("A reservation with recorded payments must be changed by an administrator.");
        }

        Room room = rooms.findByIdAndActiveTrue(roomId)
                .orElseThrow(() -> new RoomNotFoundException("This room is not available."));
        if (!room.isBookable()) {
            throw new IllegalStateException("This room is not ready for guests or is under maintenance.");
        }
        validateDates(checkIn, checkOut);
        if (guests == null || guests < 1 || guests > room.getCapacity()) {
            throw new IllegalArgumentException("Guest count must be between 1 and the room capacity.");
        }
        if (isUnavailable(roomId, checkIn, checkOut, reservationId)) {
            throw new IllegalArgumentException("This room is already reserved for some or all of those dates.");
        }

        int nights = Math.toIntExact(ChronoUnit.DAYS.between(checkIn, checkOut));
        BigDecimal total = room.getNightlyRate().multiply(BigDecimal.valueOf(nights));
        ReservationStatus previousStatus = reservation.getStatus();
        String previousStay = reservation.getRoomCode() + " " + reservation.getCheckInDate()
                + " to " + reservation.getCheckOutDate() + ", guests=" + reservation.getGuests();
        String updatedStay = room.getRoomCode() + " " + checkIn + " to " + checkOut + ", guests=" + guests;
        if (!java.util.Objects.equals(reservation.getRoomId(), room.getId())) {
            rooms.findById(reservation.getRoomId()).ifPresent(Room::touchAvailability);
        }
        room.touchAvailability();
        reservation.modifyStay(room.getId(), room.getRoomCode(), room.getName(), checkIn, checkOut,
                guests, nights, room.getNightlyRate(), total,
                stayTimes.checkInTime(), stayTimes.checkOutTime());
        audit.record(username, "RESERVATION_MODIFIED", "RESERVATION", reservationId.toString(),
                "Previous: " + previousStay + ". Updated: " + updatedStay
                        + ". Previous status: " + previousStatus + "; updated request requires approval.");
        notifications.notifyAdmins("Reservation changed",
                "Customer " + username + " modified reservation #" + reservationId + ". Review the updated request.",
                "RESERVATION");
        return reservation;
    }

    private void ensureModifiable(Reservation reservation) {
        if (reservation.getStatus() != ReservationStatus.PENDING
                && reservation.getStatus() != ReservationStatus.APPROVED) {
            throw new IllegalStateException("This reservation can no longer be modified online.");
        }
        LocalDateTime scheduledCheckIn = reservation.getCheckInDate().atTime(reservation.getCheckInTime());
        if (!LocalDateTime.now(clock).isBefore(scheduledCheckIn)) {
            throw new IllegalStateException("Reservations at or after check-in time require administrator assistance.");
        }
    }

    @Transactional(readOnly = true)
    public List<Reservation> customerReservations(String username) {
        SystemUser customer = users.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new IllegalArgumentException("Customer account was not found."));
        return reservations.findByCustomerIdOrderByCreatedAtDesc(customer.getId());
    }

    @Transactional(readOnly = true)
    public List<Reservation> roomOccupancy(Long roomId) {
        return reservations.findByRoomIdAndStatusInOrderByCheckInDateAsc(roomId,
                BLOCKING_STATUSES);
    }

    @Transactional(readOnly = true)
    public List<Reservation> allReservations() {
        return reservations.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Reservation> searchReservations(String query, ReservationStatus status,
                                                ReservationSource source, LocalDate from, LocalDate to) {
        String cleanQuery = query == null || query.isBlank() ? null : query.trim();
        if (cleanQuery != null && cleanQuery.length() > 120) {
            throw new IllegalArgumentException("Search text must be 120 characters or fewer.");
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("The end date must be on or after the start date.");
        }
        List<Reservation> results = reservations.searchReservations(cleanQuery, status, source, from, to);
        // Unpaid checkout holds reserve dates but are not yet manager-reviewable reservations.
        return status == null
                ? results.stream().filter(r -> r.getStatus() != ReservationStatus.PAYMENT_PENDING).toList()
                : results;
    }


    public void review(Long id, boolean approve, String adminUsername) {
        review(id, approve, adminUsername, approve ? null : "Reservation rejected by the hotel.");
    }

    public void review(Long id, boolean approve, String adminUsername, String rejectionReason) {
        String cleanRejectionReason = rejectionReason == null ? "" : rejectionReason.trim();
        if (!approve && (cleanRejectionReason.isBlank() || cleanRejectionReason.length() > 500)) {
            throw new IllegalArgumentException("Enter a rejection reason up to 500 characters.");
        }
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
            notifications.notifyUser(reservation.getCustomerUsername(), "Reservation approved",
                "Your reservation #" + id + " has been approved.", "RESERVATION");
        } else {
            reservation.changeStatus(ReservationStatus.REJECTED);
            audit.record(adminUsername, "RESERVATION_REJECTED", "RESERVATION", id.toString(),
                "Rejected reservation for " + reservation.getCustomerUsername()
                        + ". Reason: " + cleanRejectionReason);

            // A paid reservation rejection must never trigger an automatic refund.
            // Queue each eligible paid transaction for a different administrator to authorize.
            List<com.hotel.system.payment.Payment> paidPayments =
                    payments.findByReservationIdOrderByCreatedAtAsc(id).stream()
                            .filter(payment -> payment.getStatus() == PaymentStatus.PAID)
                            .toList();
            for (com.hotel.system.payment.Payment payment : paidPayments) {
                if (refundRequests.existsByPaymentIdAndStatus(payment.getId(), RefundRequestStatus.PENDING)) {
                    continue;
                }
                PaymentRefundRequest refundRequest = refundRequests.save(new PaymentRefundRequest(
                        payment.getId(), adminUsername,
                        "Reservation #" + id + " was rejected. Reason: " + cleanRejectionReason
                                + ". Full payment refund requires authorization by a different administrator."));
                audit.record(adminUsername, "PAYMENT_REFUND_REQUESTED", "PAYMENT",
                        payment.getId().toString(),
                        "Refund request #" + refundRequest.getId() + " automatically created after reservation rejection #" + id
                                + ". A different administrator must authorize the refund.");
                notifications.notifyAdmins("Reservation rejection requires refund authorization",
                        "Reservation #" + id + " was rejected by " + adminUsername
                                + ". Refund request #" + refundRequest.getId() + " for payment #"
                                + payment.getId() + " is awaiting review by a different administrator.",
                        "PAYMENT");
            }
            notifications.notifyUser(reservation.getCustomerUsername(), "Reservation rejected",
                paidPayments.isEmpty()
                    ? "Your reservation #" + id + " was rejected. Contact the hotel for details."
                    : "Your reservation #" + id + " was rejected. A full refund request has been submitted for separate administrator authorization. We will notify you when it is reviewed.",
                "RESERVATION");
        }
    }

    public void cancelUnpaidCheckout(Long id, String username) {
        SystemUser customer = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        Reservation reservation = reservations.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (!java.util.Objects.equals(reservation.getCustomerId(), customer.getId())
                || reservation.getStatus() != ReservationStatus.PAYMENT_PENDING) {
            throw new IllegalStateException("This payment hold can no longer be cancelled.");
        }
        reservation.changeStatus(ReservationStatus.EXPIRED);
        audit.record(username, "RESERVATION_PAYMENT_HOLD_CANCELLED", "RESERVATION", id.toString(),
                "Checkout could not be started; the unpaid room hold was released.");
    }

    public void cancelCustomerReservation(Long id, String username) {
        SystemUser customer = users.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new IllegalArgumentException("Customer account was not found."));
        Reservation reservation = reservations.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (!java.util.Objects.equals(reservation.getCustomerId(), customer.getId())) {
            throw new IllegalArgumentException("Reservation not found.");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING
                && reservation.getStatus() != ReservationStatus.APPROVED) {
            throw new IllegalStateException("This reservation can no longer be cancelled online.");
        }
        LocalDateTime scheduledCheckIn = reservation.getCheckInDate().atTime(reservation.getCheckInTime());
        if (!LocalDateTime.now(clock).isBefore(scheduledCheckIn)) {
            throw new IllegalStateException("Reservations at or after check-in time require admin assistance to cancel.");
        }
        if (payments.sumPaidForReservation(id, PaymentStatus.PAID).signum() > 0) {
            throw new IllegalStateException("A reservation with recorded payments requires administrator assistance for cancellation and refund review.");
        }
        reservation.changeStatus(ReservationStatus.CANCELLED);
        audit.record(username, "RESERVATION_CANCELLED", "RESERVATION", id.toString(),
            "Customer cancelled reservation.");
        notifications.notifyAdmins("Reservation cancelled", "Customer " + username
            + " cancelled reservation #" + id + ".", "RESERVATION");
    }



    @Transactional(readOnly = true)
    public Reservation adminReservationForModification(Long id) {
        Reservation reservation = reservations.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (reservation.getStatus() != ReservationStatus.PENDING
                && reservation.getStatus() != ReservationStatus.APPROVED) {
            throw new IllegalStateException("Only pending or approved reservations can be modified.");
        }
        if (reservation.getCheckedInAt() != null || reservation.getCheckedOutAt() != null) {
            throw new IllegalStateException("A stay that has started or ended cannot be modified.");
        }
        return reservation;
    }

    public Reservation adminModifyReservation(Long id, String adminUsername, Long roomId,
                                               LocalDate checkIn, LocalDate checkOut, Integer guests) {
        Reservation reservation = reservations.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (reservation.getStatus() != ReservationStatus.PENDING
                && reservation.getStatus() != ReservationStatus.APPROVED) {
            throw new IllegalStateException("Only pending or approved reservations can be modified.");
        }
        if (reservation.getCheckedInAt() != null || reservation.getCheckedOutAt() != null) {
            throw new IllegalStateException("A stay that has started or ended cannot be modified.");
        }
        boolean hasFinancialActivity = payments.findByReservationIdOrderByCreatedAtAsc(id).stream()
                .anyMatch(payment -> payment.getStatus() != PaymentStatus.FAILED
                        && payment.getStatus() != PaymentStatus.VOIDED);
        if (hasFinancialActivity) {
            throw new IllegalStateException("This reservation has payment activity. Void or refund the related transactions before modifying the stay.");
        }
        Room room = rooms.findByIdAndActiveTrue(roomId)
                .orElseThrow(() -> new RoomNotFoundException("The selected room is not available."));
        if (!room.isBookable()) {
            throw new IllegalStateException("The selected room is not ready for guests or is under maintenance.");
        }
        validateDates(checkIn, checkOut);
        if (guests == null || guests < 1 || guests > room.getCapacity()) {
            throw new IllegalArgumentException("Guest count must be between 1 and the room capacity.");
        }
        if (isUnavailable(roomId, checkIn, checkOut, id)) {
            throw new IllegalArgumentException("The selected room is already reserved for some or all of those dates.");
        }
        ReservationStatus previousStatus = reservation.getStatus();
        String before = reservation.getRoomCode() + " " + reservation.getCheckInDate()
                + " to " + reservation.getCheckOutDate() + ", guests=" + reservation.getGuests()
                + ", total=" + reservation.getTotalAmount();
        int nights = Math.toIntExact(ChronoUnit.DAYS.between(checkIn, checkOut));
        BigDecimal total = room.getNightlyRate().multiply(BigDecimal.valueOf(nights));
        if (!java.util.Objects.equals(reservation.getRoomId(), room.getId())) {
            rooms.findById(reservation.getRoomId()).ifPresent(Room::touchAvailability);
        }
        room.touchAvailability();
        reservation.modifyStay(room.getId(), room.getRoomCode(), room.getName(), checkIn, checkOut,
                guests, nights, room.getNightlyRate(), total,
                stayTimes.checkInTime(), stayTimes.checkOutTime());
        reservation.changeStatus(previousStatus);
        audit.record(adminUsername, "ADMIN_RESERVATION_MODIFIED", "RESERVATION", id.toString(),
                "Before: " + before + ". After: " + room.getRoomCode() + " " + checkIn + " to "
                        + checkOut + ", guests=" + guests + ", total=" + total
                        + ". Status preserved as " + previousStatus + ".");
        if (reservation.getSource() == ReservationSource.ONLINE) {
            notifications.notifyUser(reservation.getCustomerUsername(), "Reservation updated by the hotel",
                    "Your reservation #" + id + " was updated by the hotel. New stay: "
                            + checkIn + " to " + checkOut + ". New total: " + total + ".",
                    "RESERVATION");
        }
        return reservation;
    }

    public void cancelByAdmin(Long id, String adminUsername, String reason) {
        Reservation reservation = reservations.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (reservation.getStatus() != ReservationStatus.PENDING
                && reservation.getStatus() != ReservationStatus.APPROVED) {
            throw new IllegalStateException("Only pending or approved reservations can be cancelled by an administrator.");
        }
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isBlank() || cleanReason.length() > 500) {
            throw new IllegalArgumentException("Enter a cancellation reason up to 500 characters.");
        }
        if (payments.sumPaidForReservation(id, PaymentStatus.PAID).signum() > 0) {
            throw new IllegalStateException("Refund or void recorded payments before cancelling this reservation.");
        }
        reservation.changeStatus(ReservationStatus.CANCELLED);
        audit.record(adminUsername, "RESERVATION_CANCELLED_BY_ADMIN", "RESERVATION", id.toString(),
                "Cancelled reservation for " + reservation.getGuestName() + ". Reason: " + cleanReason);
        if (reservation.getSource() == ReservationSource.ONLINE) {
            notifications.notifyUser(reservation.getCustomerUsername(), "Reservation cancelled",
                    "Your reservation #" + id + " was cancelled by the hotel. Reason: " + cleanReason,
                    "RESERVATION");
        }
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
        if (!room.isBookable()) {
            throw new IllegalStateException("This room is not ready for guests or is under maintenance.");
        }
        validateWalkInDates(checkIn, checkOut);
        if (guests == null || guests < 1 || guests > room.getCapacity())
            throw new IllegalArgumentException("Guest count must be between 1 and the room capacity.");
        if (isUnavailable(roomId, checkIn, checkOut))
            throw new IllegalArgumentException("This room is already reserved for some or all of those dates.");
        int nights = Math.toIntExact(ChronoUnit.DAYS.between(checkIn, checkOut));
        BigDecimal total = room.getNightlyRate().multiply(BigDecimal.valueOf(nights));
        room.touchAvailability();
        Reservation reservation = new Reservation(room.getId(), name, email, adminUsername,
            room.getRoomCode(), room.getName(), checkIn, checkOut, guests, nights, room.getNightlyRate(), total);
        reservation.setStayTimes(stayTimes.checkInTime(), stayTimes.checkOutTime());
        Reservation saved = reservations.save(reservation);
        audit.record(adminUsername, "WALK_IN_RESERVATION_CREATED", "RESERVATION", saved.getId().toString(),
            "Walk-in guest " + name + "; room " + room.getRoomCode());
        return saved;
    }

    public void checkIn(Long id, String adminUsername) {
        Reservation reservation = reservations.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (reservation.getStatus() != ReservationStatus.APPROVED)
            throw new IllegalStateException("Only approved reservations can be checked in.");
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime scheduledCheckIn = reservation.getCheckInDate().atTime(reservation.getCheckInTime());
        LocalDateTime scheduledCheckOut = reservation.getCheckOutDate().atTime(reservation.getCheckOutTime());
        if (!now.isBefore(scheduledCheckOut)) {
            throw new IllegalStateException("The scheduled checkout time has passed. Create a new reservation for a future stay.");
        }
        boolean sameDayWalkIn = reservation.getSource() == ReservationSource.WALK_IN
                && reservation.getCheckInDate().equals(now.toLocalDate());
        if (now.isBefore(scheduledCheckIn) && !sameDayWalkIn)
            throw new IllegalStateException("The scheduled check-in time has not started.");
        if (payments.sumPaidForReservation(id, PaymentStatus.PAID).compareTo(reservation.getTotalAmount()) < 0)
            throw new IllegalStateException("Full payment must be recorded before check-in.");
        reservation.recordCheckIn(java.time.Instant.now(clock));
        audit.record(adminUsername, "GUEST_CHECKED_IN", "RESERVATION", id.toString(),
            "Checked in guest " + reservation.getGuestName() + " at " + reservation.getCheckedInAt());
        if (reservation.getSource() == ReservationSource.ONLINE) {
            notifications.notifyUser(reservation.getCustomerUsername(), "Check-in completed",
                    "Your check-in for reservation #" + id + " has been recorded.", "RESERVATION");
        }
    }

    public void checkOut(Long id, String adminUsername) {
        Reservation reservation = reservations.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (reservation.getStatus() != ReservationStatus.CHECKED_IN)
            throw new IllegalStateException("Only checked-in guests can be checked out.");
        if (payments.sumPaidForReservation(id, PaymentStatus.PAID).compareTo(reservation.getTotalAmount()) < 0)
            throw new IllegalStateException("The remaining balance must be settled before checkout.");
        reservation.recordCheckOut(java.time.Instant.now(clock));
        audit.record(adminUsername, "GUEST_CHECKED_OUT", "RESERVATION", id.toString(),
            "Checked out guest " + reservation.getGuestName() + " at " + reservation.getCheckedOutAt());
        if (reservation.getSource() == ReservationSource.ONLINE) {
            notifications.notifyUser(reservation.getCustomerUsername(), "Checkout completed",
                    "Your checkout for reservation #" + id + " has been recorded. Thank you for staying with us.",
                    "RESERVATION");
        }
    }

    public void markNoShow(Long id, String adminUsername) {
        Reservation reservation = reservations.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        LocalDateTime noShowEligibleAt = reservation.getCheckInDate()
                .atTime(reservation.getCheckInTime()).plusHours(2);
        if (reservation.getStatus() != ReservationStatus.APPROVED
                || LocalDateTime.now(clock).isBefore(noShowEligibleAt))
            throw new IllegalStateException("A reservation can be marked no-show only two hours after its scheduled check-in time.");
        reservation.changeStatus(ReservationStatus.NO_SHOW);
        audit.record(adminUsername, "RESERVATION_MARKED_NO_SHOW", "RESERVATION", id.toString(),
            "Marked guest " + reservation.getGuestName() + " as no-show.");
        if (reservation.getSource() == ReservationSource.ONLINE) {
            notifications.notifyUser(reservation.getCustomerUsername(), "Reservation marked no-show",
                    "Reservation #" + id + " was marked as a no-show. Contact the hotel if you believe this is incorrect.",
                    "RESERVATION");
        }
    }

    private void validateWalkInDates(LocalDate checkIn, LocalDate checkOut) {
        LocalDate today = LocalDate.now(clock);
        if (checkIn == null || checkOut == null || checkIn.isBefore(today))
            throw new IllegalArgumentException("Walk-in check-in date cannot be in the past.");
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
        if (checkIn == null || checkOut == null
                || checkIn.atTime(stayTimes.checkInTime()).isBefore(LocalDateTime.now(clock))) {
            throw new IllegalArgumentException("Check-in must be a future date and time.");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("Check-out must be after check-in.");
        }
        if (ChronoUnit.DAYS.between(checkIn, checkOut) > 365) {
            throw new IllegalArgumentException("A reservation cannot exceed 365 nights.");
        }
    }
}