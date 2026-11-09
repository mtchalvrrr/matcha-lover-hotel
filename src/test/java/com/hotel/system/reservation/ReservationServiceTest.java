package com.hotel.system.reservation;

import com.hotel.system.account.AccountRole;
import com.hotel.system.account.SystemUser;
import com.hotel.system.account.SystemUserRepository;
import com.hotel.system.audit.AuditService;
import com.hotel.system.config.HotelStayTimePolicy;
import com.hotel.system.notification.NotificationService;
import com.hotel.system.payment.PaymentRepository;
import com.hotel.system.payment.PaymentRefundRequestRepository;
import com.hotel.system.payment.Payment;
import com.hotel.system.payment.PaymentStatus;
import com.hotel.system.room.Room;
import com.hotel.system.room.RoomRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationServiceTest {
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final SystemUserRepository users = mock(SystemUserRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentRefundRequestRepository refundRequests = mock(PaymentRefundRequestRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final HotelStayTimePolicy stayTimes = mock(HotelStayTimePolicy.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-08T03:00:00Z"), ZoneId.of("Asia/Manila"));

    private ReservationService service() {
        when(stayTimes.checkInTime()).thenReturn(LocalTime.of(14, 0));
        when(stayTimes.checkOutTime()).thenReturn(LocalTime.NOON);
        return new ReservationService(reservations, rooms, users, clock, audit, payments, refundRequests, notifications, stayTimes);
    }

    @Test
    void customerModificationRechecksAvailabilityAndReturnsApprovedBookingToPending() {
        Reservation reservation = new Reservation(null, "guest", "guest@example.com", 1L,
                "A-01", "Old Room", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 14),
                2, 2, new BigDecimal("1000.00"), new BigDecimal("2000.00"));
        reservation.changeStatus(ReservationStatus.APPROVED);

        Room newRoom = mock(Room.class);
        when(newRoom.getId()).thenReturn(2L);
        when(newRoom.getRoomCode()).thenReturn("B-02");
        when(newRoom.getName()).thenReturn("New Room");
        when(newRoom.getCapacity()).thenReturn(3);
        when(newRoom.isBookable()).thenReturn(true);
        when(newRoom.getNightlyRate()).thenReturn(new BigDecimal("1500.00"));

        when(users.findByUsernameIgnoreCase("guest")).thenReturn(Optional.of(
                new SystemUser("guest", "guest@example.com", "encoded", AccountRole.CUSTOMER)));
        when(reservations.findById(77L)).thenReturn(Optional.of(reservation));
        when(rooms.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(newRoom));
        when(payments.sumPaidForReservation(77L, PaymentStatus.PAID)).thenReturn(BigDecimal.ZERO);
        when(reservations.existsOverlappingReservation(eq(2L), anyCollection(),
                eq(LocalDate.of(2026, 11, 15)), eq(LocalDate.of(2026, 11, 17)), eq(77L))).thenReturn(false);

        Reservation updated = service().modifyCustomerReservation(77L, "guest", 2L,
                LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 17), 3);

        assertEquals(ReservationStatus.PENDING, updated.getStatus());
        assertEquals("B-02", updated.getRoomCode());
        assertEquals(2, updated.getNights());
        assertEquals(new BigDecimal("3000.00"), updated.getTotalAmount());
        assertEquals(LocalTime.of(14, 0), updated.getCheckInTime());
        verify(notifications).notifyAdmins(eq("Reservation changed"), contains("#77"), eq("RESERVATION"));
    }

    @Test
    void adminCanEditUnpaidReservationAndPreservesApprovedStatus() {
        Reservation reservation = new Reservation(null, "guest", "guest@example.com", 1L,
                "A-01", "Old Room", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 14),
                2, 2, new BigDecimal("1000.00"), new BigDecimal("2000.00"));
        reservation.changeStatus(ReservationStatus.APPROVED);
        Room selectedRoom = mock(Room.class);
        when(selectedRoom.getId()).thenReturn(2L);
        when(selectedRoom.getRoomCode()).thenReturn("B-02");
        when(selectedRoom.getName()).thenReturn("New Room");
        when(selectedRoom.getCapacity()).thenReturn(3);
        when(selectedRoom.isBookable()).thenReturn(true);
        when(selectedRoom.getNightlyRate()).thenReturn(new BigDecimal("1500.00"));
        when(reservations.findById(77L)).thenReturn(Optional.of(reservation));
        when(payments.findByReservationIdOrderByCreatedAtAsc(77L)).thenReturn(java.util.List.of());
        when(rooms.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(selectedRoom));
        when(reservations.existsOverlappingReservation(eq(2L), anyCollection(),
                eq(LocalDate.of(2026, 11, 15)), eq(LocalDate.of(2026, 11, 17)), eq(77L))).thenReturn(false);

        Reservation updated = service().adminModifyReservation(77L, "admin", 2L,
                LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 17), 3);

        assertEquals(ReservationStatus.APPROVED, updated.getStatus());
        assertEquals("B-02", updated.getRoomCode());
        assertEquals(new BigDecimal("3000.00"), updated.getTotalAmount());
        verify(audit).record(eq("admin"), eq("ADMIN_RESERVATION_MODIFIED"), eq("RESERVATION"), eq("77"), contains("Before:"));
        verify(notifications).notifyUser(eq("guest"), eq("Reservation updated by the hotel"), contains("New stay"), eq("RESERVATION"));
    }

    @Test
    void adminCannotEditReservationWithPendingOrRecordedPaymentActivity() {
        Reservation reservation = new Reservation(null, "guest", "guest@example.com", 1L,
                "A-01", "Old Room", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 14),
                2, 2, new BigDecimal("1000.00"), new BigDecimal("2000.00"));
        when(reservations.findById(77L)).thenReturn(Optional.of(reservation));
        com.hotel.system.payment.Payment pending = mock(com.hotel.system.payment.Payment.class);
        when(pending.getStatus()).thenReturn(PaymentStatus.PENDING);
        when(payments.findByReservationIdOrderByCreatedAtAsc(77L)).thenReturn(java.util.List.of(pending));

        assertThrows(IllegalStateException.class, () -> service().adminModifyReservation(77L, "admin", 2L,
                LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 17), 2));
        verify(rooms, never()).findByIdAndActiveTrue(anyLong());
    }

    @Test
    void rejectingPaidReservationQueuesRefundForDifferentAdministrator() {
        Reservation reservation = new Reservation(null, "guest", "guest@example.com", 1L,
                "A-01", "Room", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 14),
                2, 2, new BigDecimal("1000.00"), new BigDecimal("2000.00"));
        Payment payment = mock(Payment.class);
        when(payment.getId()).thenReturn(501L);
        when(payment.getStatus()).thenReturn(PaymentStatus.PAID);
        when(reservations.findById(91L)).thenReturn(Optional.of(reservation));
        when(payments.findByReservationIdOrderByCreatedAtAsc(91L)).thenReturn(java.util.List.of(payment));
        when(refundRequests.existsByPaymentIdAndStatus(501L,
                com.hotel.system.payment.RefundRequestStatus.PENDING)).thenReturn(false);
        when(refundRequests.save(any(com.hotel.system.payment.PaymentRefundRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().review(91L, false, "manager-one");

        assertEquals(ReservationStatus.REJECTED, reservation.getStatus());
        verify(refundRequests).save(argThat(request ->
                request.getPaymentId().equals(501L)
                        && request.getRequestedBy().equals("manager-one")
                        && request.getReason().contains("Reservation #91")));
        verify(notifications).notifyAdmins(contains("refund authorization"), contains("Refund request #"),
                eq("PAYMENT"));
        verify(notifications).notifyUser(eq("guest"), eq("Reservation rejected"),
                contains("full refund request"), eq("RESERVATION"));
    }

    @Test
    void customerReservationIsHeldForPaymentAndNotSentToManagerUntilConfirmed() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(9L);
        when(room.getRoomCode()).thenReturn("A-09");
        when(room.getName()).thenReturn("Matcha Suite");
        when(room.getCapacity()).thenReturn(2);
        when(room.isBookable()).thenReturn(true);
        when(room.getNightlyRate()).thenReturn(new BigDecimal("2500.00"));
        when(users.findByUsernameIgnoreCase("guest")).thenReturn(Optional.of(
                new SystemUser("guest", "guest@example.com", "encoded", AccountRole.CUSTOMER)));
        when(rooms.findByIdAndActiveTrue(9L)).thenReturn(Optional.of(room));
        when(reservations.existsOverlappingReservation(eq(9L), anyCollection(),
                eq(LocalDate.of(2026, 11, 12)), eq(LocalDate.of(2026, 11, 14)), isNull())).thenReturn(false);
        when(reservations.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation saved = invocation.getArgument(0);
            try {
                java.lang.reflect.Field id = Reservation.class.getDeclaredField("id");
                id.setAccessible(true);
                id.set(saved, 55L);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
            return saved;
        });

        Reservation hold = service().request("guest", 9L,
                LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 14), 2);

        assertEquals(ReservationStatus.PAYMENT_PENDING, hold.getStatus());
        assertEquals(new BigDecimal("5000.00"), hold.getTotalAmount());
        verify(notifications, never()).notifyAdmins(anyString(), anyString(), anyString());
        verify(audit).record(eq("guest"), eq("RESERVATION_CHECKOUT_STARTED"),
                eq("RESERVATION"), anyString(), contains("full payment"));
    }

    @Test
    void customerCannotReserveRoomThatIsNotReadyForGuests() {
        Room room = mock(Room.class);
        when(room.isBookable()).thenReturn(false);
        when(users.findByUsernameIgnoreCase("guest")).thenReturn(Optional.of(
                new SystemUser("guest", "guest@example.com", "encoded", AccountRole.CUSTOMER)));
        when(rooms.findByIdAndActiveTrue(9L)).thenReturn(Optional.of(room));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service().request("guest", 9L, LocalDate.of(2026, 11, 12),
                        LocalDate.of(2026, 11, 14), 1));

        assertTrue(error.getMessage().contains("not ready"));
        verify(reservations, never()).save(any(Reservation.class));
    }

    @Test
    void adminCanCancelUnpaidApprovedReservationWithReason() {
        Reservation reservation = new Reservation(null, "guest", "guest@example.com", 1L,
                "A-01", "Room", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 14),
                2, 2, new BigDecimal("1000.00"), new BigDecimal("2000.00"));
        reservation.changeStatus(ReservationStatus.APPROVED);
        when(reservations.findById(88L)).thenReturn(Optional.of(reservation));
        when(payments.sumPaidForReservation(88L, PaymentStatus.PAID)).thenReturn(BigDecimal.ZERO);

        service().cancelByAdmin(88L, "admin", "Room unavailable");

        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        verify(notifications).notifyUser(eq("guest"), eq("Reservation cancelled"), contains("Room unavailable"), eq("RESERVATION"));
    }

    @Test
    void adminCannotCancelReservationUntilRecordedPaymentsAreRefunded() {
        Reservation reservation = new Reservation(null, "guest", "guest@example.com", 1L,
                "A-01", "Room", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 14),
                2, 2, new BigDecimal("1000.00"), new BigDecimal("2000.00"));
        when(reservations.findById(88L)).thenReturn(Optional.of(reservation));
        when(payments.sumPaidForReservation(88L, PaymentStatus.PAID)).thenReturn(new BigDecimal("1000.00"));

        assertThrows(IllegalStateException.class, () -> service().cancelByAdmin(88L, "admin", "Guest request"));
        assertEquals(ReservationStatus.PENDING, reservation.getStatus());
    }

    @Test
    void customerCannotModifyReservationAfterPaymentHasBeenRecorded() {
        Reservation reservation = new Reservation(null, "guest", "guest@example.com", 1L,
                "A-01", "Old Room", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 14),
                2, 2, new BigDecimal("1000.00"), new BigDecimal("2000.00"));
        when(users.findByUsernameIgnoreCase("guest")).thenReturn(Optional.of(
                new SystemUser("guest", "guest@example.com", "encoded", AccountRole.CUSTOMER)));
        when(reservations.findById(77L)).thenReturn(Optional.of(reservation));
        when(payments.sumPaidForReservation(77L, PaymentStatus.PAID)).thenReturn(new BigDecimal("1000.00"));

        assertThrows(IllegalStateException.class, () -> service().modifyCustomerReservation(77L, "guest", 2L,
                LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 17), 2));
        verify(rooms, never()).findByIdAndActiveTrue(anyLong());
    }
}
