package com.hotel.system.reservation;

import com.hotel.system.audit.AuditService;
import com.hotel.system.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PendingReservationExpirySchedulerTest {
    @Test
    void expiresStalePendingRequestsAndNotifiesOnlineCustomer() {
        ReservationRepository reservations = mock(ReservationRepository.class);
        AuditService audit = mock(AuditService.class);
        NotificationService notifications = mock(NotificationService.class);
        Instant now = Instant.parse("2026-11-08T03:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("Asia/Manila"));
        Reservation stale = new Reservation(5L, "guest", "guest@example.com", 8L,
                "A-08", "Garden Room", LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 2), 1, 1,
                new BigDecimal("1500.00"), new BigDecimal("1500.00"));
        ReflectionTestUtils.setField(stale, "id", 88L);
        ReflectionTestUtils.setField(stale, "createdAt", now.minusSeconds(25 * 3600L));
        ReflectionTestUtils.setField(stale, "updatedAt", now.minusSeconds(25 * 3600L));
        when(reservations.findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                ReservationStatus.PENDING, now.minusSeconds(24 * 3600L))).thenReturn(List.of(stale));

        PendingReservationExpiryScheduler scheduler = new PendingReservationExpiryScheduler(
                reservations, audit, notifications, clock, 24);
        scheduler.expireStalePendingReservations();

        assertEquals(ReservationStatus.EXPIRED, stale.getStatus());
        assertEquals(now, stale.getUpdatedAt());
        verify(audit).record("SYSTEM", "RESERVATION_EXPIRED", "RESERVATION", "88",
                "Pending reservation expired after 24 hours without approval.");
        verify(notifications).notifyUser("guest", "Reservation request expired",
                "Reservation #88 expired because it was not reviewed within 24 hours. You may submit a new request.",
                "RESERVATION");
    }


    @Test
    void marksPendingReservationVoidWhenCheckInTimeArrivesBeforeAdminReview() {
        ReservationRepository reservations = mock(ReservationRepository.class);
        AuditService audit = mock(AuditService.class);
        NotificationService notifications = mock(NotificationService.class);
        Instant now = Instant.parse("2026-11-08T03:30:00Z"); // 11:30 AM in Manila
        Clock clock = Clock.fixed(now, ZoneId.of("Asia/Manila"));
        Reservation due = new Reservation(5L, "guest", "guest@example.com", 8L,
                "A-08", "Garden Room", LocalDate.of(2026, 11, 8),
                LocalDate.of(2026, 11, 9), 1, 1,
                new BigDecimal("1500.00"), new BigDecimal("1500.00"));
        due.setStayTimes(java.time.LocalTime.of(10, 0), java.time.LocalTime.NOON);
        ReflectionTestUtils.setField(due, "id", 90L);
        when(reservations.findByStatusAndCheckInDateLessThanEqualOrderByCheckInDateAsc(
                ReservationStatus.PENDING, LocalDate.of(2026, 11, 8))).thenReturn(List.of(due));

        PendingReservationExpiryScheduler scheduler = new PendingReservationExpiryScheduler(
                reservations, audit, notifications, clock, 24);
        scheduler.expireStalePendingReservations();

        assertEquals(ReservationStatus.VOID, due.getStatus());
        assertEquals(now, due.getUpdatedAt());
        verify(audit).record("SYSTEM", "RESERVATION_VOIDED_AT_CHECK_IN", "RESERVATION", "90",
                "Reservation became VOID because scheduled check-in arrived before Admin review.");
        verify(notifications).notifyUser("guest", "Reservation marked VOID",
                "Reservation #90 was marked VOID because its check-in time arrived before the hotel Admin reviewed it.",
                "RESERVATION");
    }

    @Test
    void doesNotExpireRecentlyModifiedPendingRequestBasedOnItsOriginalCreationDate() {
        ReservationRepository reservations = mock(ReservationRepository.class);
        AuditService audit = mock(AuditService.class);
        NotificationService notifications = mock(NotificationService.class);
        Instant now = Instant.parse("2026-11-08T03:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("Asia/Manila"));
        Reservation recentlyModified = new Reservation(5L, "guest", "guest@example.com", 8L,
                "A-08", "Garden Room", LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 2), 1, 1,
                new BigDecimal("1500.00"), new BigDecimal("1500.00"));
        ReflectionTestUtils.setField(recentlyModified, "id", 89L);
        ReflectionTestUtils.setField(recentlyModified, "createdAt", now.minusSeconds(48 * 3600L));
        ReflectionTestUtils.setField(recentlyModified, "updatedAt", now.minusSeconds(60 * 60L));
        when(reservations.findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                ReservationStatus.PENDING, now.minusSeconds(24 * 3600L))).thenReturn(List.of());

        PendingReservationExpiryScheduler scheduler = new PendingReservationExpiryScheduler(
                reservations, audit, notifications, clock, 24);
        scheduler.expireStalePendingReservations();

        assertEquals(ReservationStatus.PENDING, recentlyModified.getStatus());
        verify(reservations).findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                ReservationStatus.PENDING, now.minusSeconds(24 * 3600L));
        verifyNoInteractions(audit, notifications);
    }

    @Test
    void rejectsUnsafeExpiryConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new PendingReservationExpiryScheduler(
                mock(ReservationRepository.class), mock(AuditService.class),
                mock(NotificationService.class), Clock.systemUTC(), 0));
        assertThrows(IllegalArgumentException.class, () -> new PendingReservationExpiryScheduler(
                mock(ReservationRepository.class), mock(AuditService.class),
                mock(NotificationService.class), Clock.systemUTC(), 721));
    }
}
