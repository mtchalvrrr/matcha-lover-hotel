package com.hotel.system.report;

import com.hotel.system.payment.Payment;
import com.hotel.system.payment.PaymentRepository;
import com.hotel.system.payment.PaymentStatus;
import com.hotel.system.payment.PaymentType;
import com.hotel.system.reservation.Reservation;
import com.hotel.system.reservation.ReservationRepository;
import com.hotel.system.reservation.ReservationSource;
import com.hotel.system.reservation.ReservationStatus;
import com.hotel.system.room.RoomRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportServiceTest {
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneId.of("Asia/Manila"));
    private final ReportService service = new ReportService(reservations, payments, rooms, clock);

    @Test
    void calculatesBookingChannelsAndRoomNightCapacityForOverlappingStays() {
        LocalDate from = LocalDate.of(2026, 11, 10);
        LocalDate to = LocalDate.of(2026, 11, 12);

        Reservation onlineBooking = new Reservation(7L, "guest", "guest@example.com",
                3L, "A-01", "Garden Room", from, from.plusDays(1), 2, 1,
                new BigDecimal("1500.00"), new BigDecimal("1500.00"));

        Reservation overlappingStay = new Reservation(8L, "other", "other@example.com",
                4L, "A-02", "Courtyard Room", from.minusDays(1), to.plusDays(1), 2, 4,
                new BigDecimal("1200.00"), new BigDecimal("4800.00"));
        overlappingStay.changeStatus(ReservationStatus.APPROVED);

        when(reservations.findByCheckInDateBetweenOrderByCheckInDateAsc(from, to))
                .thenReturn(List.of(onlineBooking));
        when(reservations.findByCheckInDateBeforeAndCheckOutDateAfterAndStatusInOrderByCheckInDateAsc(
                to.plusDays(1), from, List.of(ReservationStatus.APPROVED,
                        ReservationStatus.CHECKED_IN, ReservationStatus.CHECKED_OUT)))
                .thenReturn(List.of(overlappingStay));
        when(payments.findByCreatedAtBetweenAndStatusOrderByCreatedAtAsc(any(), any(), any()))
                .thenReturn(List.of());
        when(rooms.countByActiveTrue()).thenReturn(2L);

        ReportSummary summary = service.summarize(from, to);

        assertEquals(1, summary.totalReservations());
        assertEquals(1, summary.pendingReservations());
        assertEquals(1, summary.onlineReservations());
        assertEquals(0, summary.walkInReservations());
        assertEquals(3, summary.reservedRoomNights());
        assertEquals(6, summary.availableRoomNights());
        assertEquals(new BigDecimal("50.00"), summary.bookedCapacityRate());
        assertEquals(BigDecimal.ZERO, summary.paidRevenue());
        assertEquals(1L, summary.bookingsByRoom().get("A-01 — Garden Room"));
    }

    @Test
    void calculatesOutstandingBalanceForApprovedReservations() {
        LocalDate from = LocalDate.of(2026, 11, 10);
        LocalDate to = LocalDate.of(2026, 11, 10);
        Reservation approved = mock(Reservation.class);
        when(approved.getId()).thenReturn(9L);
        when(approved.getStatus()).thenReturn(ReservationStatus.APPROVED);
        when(approved.getSource()).thenReturn(com.hotel.system.reservation.ReservationSource.ONLINE);
        when(approved.getRoomCode()).thenReturn("A-01");
        when(approved.getRoomName()).thenReturn("Garden Room");
        when(approved.getCheckInDate()).thenReturn(from);
        when(approved.getCheckOutDate()).thenReturn(from.plusDays(2));
        when(approved.getTotalAmount()).thenReturn(new BigDecimal("2000.00"));
        when(reservations.findByCheckInDateBetweenOrderByCheckInDateAsc(from, to))
                .thenReturn(List.of(approved));
        when(reservations.findByCheckInDateBeforeAndCheckOutDateAfterAndStatusInOrderByCheckInDateAsc(
                to.plusDays(1), from, List.of(ReservationStatus.APPROVED,
                        ReservationStatus.CHECKED_IN, ReservationStatus.CHECKED_OUT)))
                .thenReturn(List.of(approved));
        when(payments.findByCreatedAtBetweenAndStatusOrderByCreatedAtAsc(any(), any(), any()))
                .thenReturn(List.of());
        when(payments.sumPaidForReservation(9L, com.hotel.system.payment.PaymentStatus.PAID))
                .thenReturn(new BigDecimal("500.00"));
        when(rooms.countByActiveTrue()).thenReturn(1L);

        ReportSummary summary = service.summarize(from, to);

        assertEquals(new BigDecimal("1500.00"), summary.outstandingBalance());
        assertEquals(BigDecimal.ZERO, summary.refundedAmount());
    }


    @Test
    void calculatesRefundedAmountByTheDateRefundWasIssued() {
        LocalDate from = LocalDate.of(2026, 11, 8);
        LocalDate to = LocalDate.of(2026, 11, 8);
        Payment refunded = new Payment(12L, "guest", PaymentType.DOWN_PAYMENT,
                new BigDecimal("500.00"), "SIMULATED", "SIM-REFUND", PaymentStatus.PAID);
        refunded.markRefunded(Instant.parse("2026-11-08T04:00:00Z"));

        when(reservations.findByCheckInDateBetweenOrderByCheckInDateAsc(from, to)).thenReturn(List.of());
        when(reservations.findByCheckInDateBeforeAndCheckOutDateAfterAndStatusInOrderByCheckInDateAsc(
                to.plusDays(1), from, List.of(ReservationStatus.APPROVED,
                        ReservationStatus.CHECKED_IN, ReservationStatus.CHECKED_OUT))).thenReturn(List.of());
        when(payments.findByCreatedAtBetweenAndStatusOrderByCreatedAtAsc(any(), any(), eq(PaymentStatus.PAID)))
                .thenReturn(List.of());
        when(payments.findByCreatedAtBetweenAndStatusOrderByCreatedAtAsc(any(), any(), eq(PaymentStatus.REFUNDED)))
                .thenReturn(List.of());
        when(payments.findByRefundedAtBetweenOrderByRefundedAtAsc(any(), any())).thenReturn(List.of(refunded));
        when(rooms.countByActiveTrue()).thenReturn(1L);

        ReportSummary summary = service.summarize(from, to);

        assertEquals(new BigDecimal("500.00"), summary.refundedAmount());
        verify(payments).findByRefundedAtBetweenOrderByRefundedAtAsc(
                Instant.parse("2026-11-07T16:00:00Z"), Instant.parse("2026-11-08T16:00:00Z"));
    }

    @Test
    void rejectsInvalidAndOverlongDateRanges() {
        assertThrows(IllegalArgumentException.class,
                () -> service.summarize(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 10)));
        assertThrows(IllegalArgumentException.class,
                () -> service.summarize(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 3)));
    }
}
