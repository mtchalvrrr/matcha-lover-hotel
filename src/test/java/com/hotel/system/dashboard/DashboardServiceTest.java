package com.hotel.system.dashboard;

import com.hotel.system.account.AccountDeactivationRequestRepository;
import com.hotel.system.account.SystemUserRepository;
import com.hotel.system.audit.AuditLogRepository;
import com.hotel.system.payment.PaymentRepository;
import com.hotel.system.payment.PaymentStatus;
import com.hotel.system.registration.RegistrationRepository;
import com.hotel.system.reservation.ReservationRepository;
import com.hotel.system.reservation.ReservationStatus;
import com.hotel.system.room.RoomRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class DashboardServiceTest {
    @Test
    void includesArrivalsAndDeparturesForTheHotelLocalDate() {
        ReservationRepository reservations = mock(ReservationRepository.class);
        RoomRepository rooms = mock(RoomRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        SystemUserRepository users = mock(SystemUserRepository.class);
        RegistrationRepository registrations = mock(RegistrationRepository.class);
        AccountDeactivationRequestRepository deactivations = mock(AccountDeactivationRequestRepository.class);
        AuditLogRepository audit = mock(AuditLogRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-08T16:30:00Z"), ZoneId.of("Asia/Manila"));

        when(reservations.countByCheckInDateAndStatusIn(LocalDate.of(2026, 11, 9),
                List.of(ReservationStatus.APPROVED))).thenReturn(3L);
        when(reservations.countByCheckOutDateAndStatusIn(LocalDate.of(2026, 11, 9),
                List.of(ReservationStatus.CHECKED_IN))).thenReturn(2L);
        when(payments.sumAmountByStatus(PaymentStatus.PAID)).thenReturn(BigDecimal.ZERO);
        when(audit.findTop5ByOrderByCreatedAtDesc()).thenReturn(List.of());

        DashboardService service = new DashboardService(reservations, rooms, payments, users,
                registrations, deactivations, audit, clock);
        DashboardService.AdminSummary summary = service.adminSummary();

        assertEquals(3L, summary.arrivalsToday());
        assertEquals(2L, summary.departuresToday());
        verify(reservations).countByCheckInDateAndStatusIn(LocalDate.of(2026, 11, 9),
                List.of(ReservationStatus.APPROVED));
        verify(reservations).countByCheckOutDateAndStatusIn(LocalDate.of(2026, 11, 9),
                List.of(ReservationStatus.CHECKED_IN));
    }
}
