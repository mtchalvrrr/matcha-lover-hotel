package com.hotel.system.report;

import com.hotel.system.payment.PaymentRepository;
import com.hotel.system.payment.PaymentStatus;
import com.hotel.system.reservation.Reservation;
import com.hotel.system.reservation.ReservationRepository;
import com.hotel.system.reservation.ReservationStatus;
import com.hotel.system.room.RoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportReservationStatusMetricsTest {
    @Test
    void reportsNoShowAndCompletedStayStatusesSeparately() {
        ReservationRepository reservations = mock(ReservationRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        RoomRepository rooms = mock(RoomRepository.class);
        LocalDate date = LocalDate.of(2026, 11, 10);

        Reservation noShow = booking(1L, date, ReservationStatus.NO_SHOW);
        Reservation checkedIn = booking(2L, date, ReservationStatus.CHECKED_IN);
        Reservation checkedOut = booking(3L, date, ReservationStatus.CHECKED_OUT);
        when(reservations.findByCheckInDateBetweenOrderByCheckInDateAsc(date, date))
                .thenReturn(List.of(noShow, checkedIn, checkedOut));
        when(reservations.findByCheckInDateBeforeAndCheckOutDateAfterAndStatusInOrderByCheckInDateAsc(
                date.plusDays(1), date, List.of(ReservationStatus.APPROVED,
                        ReservationStatus.CHECKED_IN, ReservationStatus.CHECKED_OUT)))
                .thenReturn(List.of(checkedIn, checkedOut));
        when(payments.findByCreatedAtBetweenAndStatusOrderByCreatedAtAsc(any(), any(), any()))
                .thenReturn(List.of());
        when(rooms.countByActiveTrue()).thenReturn(3L);
        when(payments.sumPaidForReservation(anyLong(), eq(PaymentStatus.PAID))).thenReturn(BigDecimal.ZERO);

        ReportSummary summary = new ReportService(reservations, payments, rooms,
                Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneId.of("Asia/Manila")))
                .summarize(date, date);

        assertEquals(1, summary.noShowReservations());
        assertEquals(1, summary.checkedInReservations());
        assertEquals(1, summary.checkedOutReservations());
    }

    private Reservation booking(Long id, LocalDate date, ReservationStatus status) {
        Reservation reservation = new Reservation(id, "guest-" + id, "guest" + id + "@example.com",
                id, "R-" + id, "Room " + id, date, date.plusDays(1), 1, 1,
                new BigDecimal("1000.00"), new BigDecimal("1000.00"));
        ReflectionTestUtils.setField(reservation, "id", id);
        reservation.changeStatus(status);
        return reservation;
    }
}
