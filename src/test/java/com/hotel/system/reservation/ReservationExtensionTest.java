package com.hotel.system.reservation;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class ReservationExtensionTest {
    @Test
    void extendingCheckoutRecalculatesNightsAndTotalWithoutChangingApprovedStatus() {
        Reservation reservation = new Reservation(10L, "guest", "guest@example.com",
                1L, "101", "Deluxe", LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 3), 2, 2,
                new BigDecimal("2500.00"), new BigDecimal("5000.00"));
        reservation.changeStatus(ReservationStatus.APPROVED);

        reservation.extendCheckout(LocalDate.of(2026, 12, 5));

        assertEquals(LocalDate.of(2026, 12, 5), reservation.getCheckOutDate());
        assertEquals(4, reservation.getNights());
        assertEquals(new BigDecimal("10000.00"), reservation.getTotalAmount());
        assertEquals(ReservationStatus.APPROVED, reservation.getStatus());
    }

    @Test
    void extensionRejectsCheckoutDateThatDoesNotMoveForward() {
        Reservation reservation = new Reservation(10L, "guest", "guest@example.com",
                1L, "101", "Deluxe", LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 3), 2, 2,
                new BigDecimal("2500.00"), new BigDecimal("5000.00"));

        assertThrows(IllegalArgumentException.class,
                () -> reservation.extendCheckout(LocalDate.of(2026, 12, 3)));
    }
}
