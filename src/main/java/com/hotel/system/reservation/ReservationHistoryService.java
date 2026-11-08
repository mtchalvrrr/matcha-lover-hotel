package com.hotel.system.reservation;

import com.hotel.system.audit.AuditLog;
import com.hotel.system.audit.AuditLogRepository;
import com.hotel.system.payment.Payment;
import com.hotel.system.payment.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ReservationHistoryService {
    private static final Set<String> CUSTOMER_VISIBLE_ACTIONS = Set.of(
            "RESERVATION_REQUESTED",
            "RESERVATION_MODIFIED",
            "RESERVATION_APPROVED",
            "RESERVATION_REJECTED",
            "RESERVATION_CANCELLED",
            "RESERVATION_CANCELLED_BY_ADMIN",
            "RESERVATION_EXPIRED",
            "GUEST_CHECKED_IN",
            "GUEST_CHECKED_OUT",
            "RESERVATION_MARKED_NO_SHOW"
    );
    private final ReservationRepository reservations;
    private final AuditLogRepository audit;
    private final PaymentRepository payments;

    public ReservationHistoryService(ReservationRepository reservations,
                                     AuditLogRepository audit, PaymentRepository payments) {
        this.reservations = reservations;
        this.audit = audit;
        this.payments = payments;
    }

    public HistoryView customerHistory(Long reservationId, String username) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (!reservation.getCustomerUsername().equalsIgnoreCase(username)) {
            throw new IllegalArgumentException("Reservation not found.");
        }
        return view(reservation, false);
    }

    public HistoryView adminHistory(Long reservationId) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        return view(reservation, true);
    }

    private HistoryView view(Reservation reservation, boolean includeInternalEvents) {
        List<AuditLog> events = audit.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
                "RESERVATION", reservation.getId().toString());
        if (!includeInternalEvents) {
            events = events.stream()
                    .filter(event -> CUSTOMER_VISIBLE_ACTIONS.contains(event.getAction()))
                    .toList();
        }
        return new HistoryView(reservation,
                events,
                payments.findByReservationIdOrderByCreatedAtAsc(reservation.getId()));
    }

    public record HistoryView(Reservation reservation, List<AuditLog> events, List<Payment> payments) { }
}
