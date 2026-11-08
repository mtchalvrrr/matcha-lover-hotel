package com.hotel.system.reservation;

import com.hotel.system.account.SystemUserRepository;
import com.hotel.system.audit.AuditService;
import com.hotel.system.config.HotelStayTimePolicy;
import com.hotel.system.notification.NotificationService;
import com.hotel.system.payment.PaymentRepository;
import com.hotel.system.payment.PaymentStatus;
import com.hotel.system.room.RoomRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationCheckInOutTest {
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final SystemUserRepository users = mock(SystemUserRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final HotelStayTimePolicy stayTimes = mock(HotelStayTimePolicy.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-08T03:00:00Z"), ZoneId.of("Asia/Manila"));

    private ReservationService service() {
        return new ReservationService(reservations, rooms, users, clock, audit, payments, notifications, stayTimes);
    }

    @Test
    void checkInStoresActualTimeAfterDepositIsPaid() {
        Reservation reservation = reservation();
        reservation.setStayTimes(java.time.LocalTime.of(10, 0), java.time.LocalTime.NOON);
        reservation.changeStatus(ReservationStatus.APPROVED);
        when(reservations.findById(91L)).thenReturn(Optional.of(reservation));
        when(payments.sumPaidForReservation(91L, PaymentStatus.PAID)).thenReturn(new BigDecimal("1000.00"));

        service().checkIn(91L, "admin");

        assertEquals(ReservationStatus.CHECKED_IN, reservation.getStatus());
        assertEquals(Instant.now(clock), reservation.getCheckedInAt());
        assertNull(reservation.getCheckedOutAt());
        verify(notifications).notifyUser("guest", "Check-in completed",
                "Your check-in for reservation #91 has been recorded.", "RESERVATION");
    }

    @Test
    void refusesCheckInAfterTheScheduledCheckoutTime() {
        Reservation reservation = reservation();
        reservation.changeStatus(ReservationStatus.APPROVED);
        when(reservations.findById(95L)).thenReturn(Optional.of(reservation));
        when(payments.sumPaidForReservation(95L, PaymentStatus.PAID)).thenReturn(new BigDecimal("1000.00"));
        Clock afterCheckout = Clock.fixed(
                Instant.parse("2026-11-10T05:00:00Z"), ZoneId.of("Asia/Manila"));
        ReservationService lateService = new ReservationService(
                reservations, rooms, users, afterCheckout, audit, payments, notifications, stayTimes);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> lateService.checkIn(95L, "admin"));

        assertTrue(error.getMessage().contains("checkout time has passed"));
        assertEquals(ReservationStatus.APPROVED, reservation.getStatus());
        assertNull(reservation.getCheckedInAt());
    }

    @Test
    void checkOutStoresActualTimeAfterFullBalanceIsPaid() {
        Reservation reservation = reservation();
        reservation.changeStatus(ReservationStatus.CHECKED_IN);
        when(reservations.findById(92L)).thenReturn(Optional.of(reservation));
        when(payments.sumPaidForReservation(92L, PaymentStatus.PAID)).thenReturn(new BigDecimal("2000.00"));

        service().checkOut(92L, "admin");

        assertEquals(ReservationStatus.CHECKED_OUT, reservation.getStatus());
        assertEquals(Instant.now(clock), reservation.getCheckedOutAt());
        verify(notifications).notifyUser("guest", "Checkout completed",
                "Your checkout for reservation #92 has been recorded. Thank you for staying with us.", "RESERVATION");
    }

    @Test
    void checkOutDoesNotChangeStatusWhenBalanceRemains() {
        Reservation reservation = reservation();
        reservation.changeStatus(ReservationStatus.CHECKED_IN);
        when(reservations.findById(93L)).thenReturn(Optional.of(reservation));
        when(payments.sumPaidForReservation(93L, PaymentStatus.PAID)).thenReturn(new BigDecimal("1500.00"));

        assertThrows(IllegalStateException.class, () -> service().checkOut(93L, "admin"));

        assertEquals(ReservationStatus.CHECKED_IN, reservation.getStatus());
        assertNull(reservation.getCheckedOutAt());
    }

    @Test
    void sameDayWalkInCanCheckInBeforeStandardHotelCheckInTime() {
        Reservation walkIn = new Reservation(1L, "Walk-in Guest", "walkin@example.com", "admin",
                "W-01", "Walk-in Room", LocalDate.of(2026, 11, 8),
                LocalDate.of(2026, 11, 9), 1, 1,
                new BigDecimal("1000.00"), new BigDecimal("1000.00"));
        when(reservations.findById(94L)).thenReturn(Optional.of(walkIn));
        when(payments.sumPaidForReservation(94L, PaymentStatus.PAID)).thenReturn(new BigDecimal("1000.00"));

        service().checkIn(94L, "admin");

        assertEquals(ReservationStatus.CHECKED_IN, walkIn.getStatus());
        assertEquals(Instant.now(clock), walkIn.getCheckedInAt());
    }

    private Reservation reservation() {
        return new Reservation(1L, "guest", "guest@example.com", 1L, "A-01", "Room",
                LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 10),
                2, 2, new BigDecimal("1000.00"), new BigDecimal("2000.00"));
    }
}
