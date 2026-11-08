package com.hotel.system.reservation;

import com.hotel.system.account.AccountRole;
import com.hotel.system.account.SystemUser;
import com.hotel.system.account.SystemUserRepository;
import com.hotel.system.audit.AuditService;
import com.hotel.system.config.HotelStayTimePolicy;
import com.hotel.system.notification.NotificationService;
import com.hotel.system.payment.PaymentRepository;
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
    private final NotificationService notifications = mock(NotificationService.class);
    private final HotelStayTimePolicy stayTimes = mock(HotelStayTimePolicy.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-08T03:00:00Z"), ZoneId.of("Asia/Manila"));

    private ReservationService service() {
        when(stayTimes.checkInTime()).thenReturn(LocalTime.of(14, 0));
        when(stayTimes.checkOutTime()).thenReturn(LocalTime.NOON);
        return new ReservationService(reservations, rooms, users, clock, audit, payments, notifications, stayTimes);
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
