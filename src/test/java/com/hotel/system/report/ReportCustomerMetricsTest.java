package com.hotel.system.report;

import com.hotel.system.payment.PaymentRepository;
import com.hotel.system.reservation.Reservation;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportCustomerMetricsTest {
    @Test
    void separatesNewAndReturningCustomersAndGroupsBookingCreationDates() {
        ReservationRepository reservations = mock(ReservationRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        RoomRepository rooms = mock(RoomRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneId.of("Asia/Manila"));
        LocalDate from = LocalDate.of(2026, 11, 10);
        LocalDate to = LocalDate.of(2026, 11, 12);

        Reservation returningGuestBooking = booking(7L, from);
        Reservation newGuestBooking = booking(8L, from.plusDays(1));
        Reservation previousStay = booking(7L, from.minusDays(30));
        previousStay.changeStatus(ReservationStatus.APPROVED);

        when(reservations.findByCheckInDateBetweenOrderByCheckInDateAsc(from, to))
                .thenReturn(List.of(returningGuestBooking, newGuestBooking));
        when(reservations.findByCheckInDateBeforeAndCheckOutDateAfterAndStatusInOrderByCheckInDateAsc(
                to.plusDays(1), from, List.of(ReservationStatus.APPROVED)))
                .thenReturn(List.of());
        when(reservations.findByCustomerIdIn(Set.of(7L, 8L)))
                .thenReturn(List.of(returningGuestBooking, newGuestBooking, previousStay));
        Reservation createdRequestOne = mock(Reservation.class);
        Reservation createdRequestTwo = mock(Reservation.class);
        when(createdRequestOne.getCreatedAt()).thenReturn(Instant.parse("2026-11-10T02:00:00Z"));
        when(createdRequestTwo.getCreatedAt()).thenReturn(Instant.parse("2026-11-11T02:00:00Z"));
        when(reservations.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(createdRequestOne, createdRequestTwo));
        when(payments.findByCreatedAtBetweenAndStatusInOrderByCreatedAtAsc(any(), any(), any()))
                .thenReturn(List.of());
        when(rooms.countByActiveTrue()).thenReturn(2L);

        ReportSummary summary = new ReportService(reservations, payments, rooms, clock).summarize(from, to);

        assertEquals(2, summary.uniqueCustomers());
        assertEquals(1, summary.returningCustomers());
        assertEquals(1, summary.newCustomers());
        assertEquals(2L, summary.bookingsByCreatedDate().values().stream().mapToLong(Long::longValue).sum());
    }

    private Reservation booking(Long customerId, LocalDate checkIn) {
        return new Reservation(customerId, "guest-" + customerId, "guest" + customerId + "@example.com",
                1L, "A-01", "Room", checkIn, checkIn.plusDays(1), 1, 1,
                new BigDecimal("1000.00"), new BigDecimal("1000.00"));
    }
}
