package com.hotel.system.reservation;

import com.hotel.system.audit.AuditLog;
import com.hotel.system.audit.AuditLogRepository;
import com.hotel.system.payment.Payment;
import com.hotel.system.payment.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReservationHistoryService {
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
        return view(reservation);
    }

    public HistoryView adminHistory(Long reservationId) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        return view(reservation);
    }

    private HistoryView view(Reservation reservation) {
        return new HistoryView(reservation,
                audit.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("RESERVATION",
                        reservation.getId().toString()),
                payments.findByReservationIdOrderByCreatedAtAsc(reservation.getId()));
    }

    public record HistoryView(Reservation reservation, List<AuditLog> events, List<Payment> payments) { }
}
