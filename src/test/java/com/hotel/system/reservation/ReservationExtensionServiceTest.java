package com.hotel.system.reservation;

import com.hotel.system.account.AccountRole;
import com.hotel.system.account.SystemUser;
import com.hotel.system.account.SystemUserRepository;
import com.hotel.system.audit.AuditService;
import com.hotel.system.notification.NotificationService;
import com.hotel.system.payment.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationExtensionServiceTest {
    private final ReservationExtensionRequestRepository extensions = mock(ReservationExtensionRequestRepository.class);
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final SystemUserRepository users = mock(SystemUserRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-08T03:00:00Z"), ZoneId.of("Asia/Manila"));

    private ReservationExtensionService service() {
        when(extensions.save(any(ReservationExtensionRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return new ReservationExtensionService(extensions, reservations, users, payments,
                audit, notifications, clock);
    }

    private Reservation reservation() {
        Reservation reservation = new Reservation(10L, "guest", "guest@example.com", 3L,
                "A-03", "Matcha Suite", LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 12), 2, 2,
                new BigDecimal("1500.00"), new BigDecimal("3000.00"));
        ReflectionTestUtils.setField(reservation, "id", 44L);
        reservation.changeStatus(ReservationStatus.APPROVED);
        return reservation;
    }

    private void customerExists() {
        when(users.findByUsernameIgnoreCase("guest")).thenReturn(Optional.of(
                new SystemUser("Guest", "User", "guest", "guest@example.com", "encoded", AccountRole.CUSTOMER)));
    }

    @Test
    void customerCanRequestAvailableExtensionAndAmountIsCalculated() {
        customerExists();
        Reservation reservation = reservation();
        when(reservations.findById(44L)).thenReturn(Optional.of(reservation));
        when(extensions.existsByReservationIdAndStatus(44L, ExtensionRequestStatus.PENDING)).thenReturn(false);
        when(reservations.existsOverlappingReservation(eq(3L), anyCollection(),
                eq(LocalDate.of(2026, 11, 10)), eq(LocalDate.of(2026, 11, 14)), eq(44L))).thenReturn(false);

        ReservationExtensionRequest request = service().request(44L, "guest", LocalDate.of(2026, 11, 14));

        assertEquals(2, request.getExtraNights());
        assertEquals(new BigDecimal("3000.00"), request.getAdditionalAmount());
        assertEquals(ExtensionRequestStatus.PENDING, request.getStatus());
        assertEquals(LocalDate.of(2026, 11, 12), reservation.getCheckOutDate(),
                "The booking must not change before approval.");
        verify(notifications).notifyAdmins(eq("Reservation extension requested"), contains("#44"), eq("RESERVATION"));
    }

    @Test
    void requestIsRejectedWhenRoomDatesAreUnavailable() {
        customerExists();
        when(reservations.findById(44L)).thenReturn(Optional.of(reservation()));
        when(extensions.existsByReservationIdAndStatus(44L, ExtensionRequestStatus.PENDING)).thenReturn(false);
        when(reservations.existsOverlappingReservation(eq(3L), anyCollection(), any(), any(), eq(44L)))
                .thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> service().request(44L, "guest", LocalDate.of(2026, 11, 14)));
        verify(extensions, never()).save(any());
    }

    @Test
    void duplicatePendingExtensionIsRejected() {
        customerExists();
        when(reservations.findById(44L)).thenReturn(Optional.of(reservation()));
        when(extensions.existsByReservationIdAndStatus(44L, ExtensionRequestStatus.PENDING)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> service().request(44L, "guest", LocalDate.of(2026, 11, 14)));
        verify(extensions, never()).save(any());
    }

    @Test
    void approvalExtendsCheckoutAndUpdatesTotalWithoutChangingReservationStatus() {
        Reservation reservation = reservation();
        ReservationExtensionRequest request = new ReservationExtensionRequest(44L, "guest",
                LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 14),
                2, new BigDecimal("3000.00"));
        when(extensions.findById(8L)).thenReturn(Optional.of(request));
        when(reservations.findById(44L)).thenReturn(Optional.of(reservation));
        when(reservations.existsOverlappingReservation(eq(3L), anyCollection(),
                eq(LocalDate.of(2026, 11, 10)), eq(LocalDate.of(2026, 11, 14)), eq(44L))).thenReturn(false);

        service().review(8L, true, "admin", "");

        assertEquals(LocalDate.of(2026, 11, 14), reservation.getCheckOutDate());
        assertEquals(4, reservation.getNights());
        assertEquals(new BigDecimal("6000.00"), reservation.getTotalAmount());
        assertEquals(ReservationStatus.APPROVED, reservation.getStatus());
        assertEquals(ExtensionRequestStatus.APPROVED, request.getStatus());
        verify(notifications).notifyUser(eq("guest"), eq("Reservation extension approved"), contains("₱3000.00"), eq("RESERVATION"));
    }

    @Test
    void rejectionRequiresReasonAndLeavesReservationUnchanged() {
        Reservation reservation = reservation();
        ReservationExtensionRequest request = new ReservationExtensionRequest(44L, "guest",
                LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 14),
                2, new BigDecimal("3000.00"));
        when(extensions.findById(8L)).thenReturn(Optional.of(request));
        when(reservations.findById(44L)).thenReturn(Optional.of(reservation));

        assertThrows(IllegalArgumentException.class, () -> service().review(8L, false, "admin", ""));
        assertEquals(LocalDate.of(2026, 11, 12), reservation.getCheckOutDate());
        assertEquals(ExtensionRequestStatus.PENDING, request.getStatus());

        service().review(8L, false, "admin", "Room is already booked.");
        assertEquals(ExtensionRequestStatus.REJECTED, request.getStatus());
        assertEquals("Room is already booked.", request.getReason());
    }

    @Test
    void approvalRechecksAvailabilityBeforeChangingReservation() {
        Reservation reservation = reservation();
        ReservationExtensionRequest request = new ReservationExtensionRequest(44L, "guest",
                LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 14),
                2, new BigDecimal("3000.00"));
        when(extensions.findById(8L)).thenReturn(Optional.of(request));
        when(reservations.findById(44L)).thenReturn(Optional.of(reservation));
        when(reservations.existsOverlappingReservation(eq(3L), anyCollection(), any(), any(), eq(44L)))
                .thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service().review(8L, true, "admin", ""));
        assertEquals(LocalDate.of(2026, 11, 12), reservation.getCheckOutDate());
        assertEquals(ExtensionRequestStatus.PENDING, request.getStatus());
    }
}
