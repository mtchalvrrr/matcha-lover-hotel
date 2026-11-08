package com.hotel.system.reservation;

import com.hotel.system.audit.AuditLog;
import com.hotel.system.audit.AuditLogRepository;
import com.hotel.system.payment.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationHistoryServiceTest {
    @Test
    void customerHistoryExcludesNonCustomerFacingAuditEvents() {
        ReservationRepository reservations = mock(ReservationRepository.class);
        AuditLogRepository audit = mock(AuditLogRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        Reservation reservation = mock(Reservation.class);
        when(reservations.findById(18L)).thenReturn(Optional.of(reservation));
        when(reservation.getId()).thenReturn(18L);
        when(reservation.getCustomerUsername()).thenReturn("guest");
        when(audit.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("RESERVATION", "18"))
                .thenReturn(List.of(
                        new AuditLog("guest", "RESERVATION_REQUESTED", "RESERVATION", "18", "Request submitted"),
                        new AuditLog("admin", "WALK_IN_RESERVATION_CREATED", "RESERVATION", "18", "Internal operation"),
                        new AuditLog("SYSTEM", "RESERVATION_EXPIRED", "RESERVATION", "18", "Request expired")
                ));

        ReservationHistoryService service = new ReservationHistoryService(reservations, audit, payments);
        ReservationHistoryService.HistoryView history = service.customerHistory(18L, "guest");

        assertEquals(List.of("RESERVATION_REQUESTED", "RESERVATION_EXPIRED"),
                history.events().stream().map(event -> event.getAction()).toList());
    }

    @Test
    void adminHistoryRetainsTheCompleteReservationAuditTrail() {
        ReservationRepository reservations = mock(ReservationRepository.class);
        AuditLogRepository audit = mock(AuditLogRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        Reservation reservation = mock(Reservation.class);
        when(reservations.findById(18L)).thenReturn(Optional.of(reservation));
        when(reservation.getId()).thenReturn(18L);
        List<AuditLog> events = List.of(
                new AuditLog("guest", "RESERVATION_REQUESTED", "RESERVATION", "18", "Request submitted"),
                new AuditLog("admin", "WALK_IN_RESERVATION_CREATED", "RESERVATION", "18", "Internal operation")
        );
        when(audit.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("RESERVATION", "18")).thenReturn(events);

        ReservationHistoryService service = new ReservationHistoryService(reservations, audit, payments);
        ReservationHistoryService.HistoryView history = service.adminHistory(18L);

        assertEquals(events, history.events());
    }
}
