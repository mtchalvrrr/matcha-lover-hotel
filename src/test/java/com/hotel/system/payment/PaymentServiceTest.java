package com.hotel.system.payment;

import com.hotel.system.account.AccountRole;
import com.hotel.system.account.SystemUser;
import com.hotel.system.account.SystemUserRepository;
import com.hotel.system.audit.AuditService;
import com.hotel.system.notification.NotificationService;
import com.hotel.system.reservation.Reservation;
import com.hotel.system.reservation.ReservationRepository;
import com.hotel.system.reservation.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentServiceTest {
    @Test
    void firstPaymentCollectsTheEntireReservationAmount() {
        PaymentRepository payments = mock(PaymentRepository.class);
        PaymentRefundRequestRepository refundRequests = mock(PaymentRefundRequestRepository.class);
        ReservationRepository reservations = mock(ReservationRepository.class);
        SystemUserRepository users = mock(SystemUserRepository.class);
        AuditService audit = mock(AuditService.class);
        NotificationService notifications = mock(NotificationService.class);
        PaymentService service = new PaymentService(payments, refundRequests, reservations, users, audit, "SIMULATED",
                notifications, Clock.fixed(java.time.Instant.parse("2026-11-08T04:00:00Z"), ZoneId.of("Asia/Manila")));

        SystemUser customer = new SystemUser("guest", "guest@example.com", "encoded", AccountRole.CUSTOMER);
        Reservation reservation = new Reservation(null, "guest", "guest@example.com", 4L,
                "R-04", "Deluxe Room", LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 12), 2, 2,
                new BigDecimal("1000.00"), new BigDecimal("2000.00"));
        reservation.changeStatus(ReservationStatus.PAYMENT_PENDING);
        when(users.findByUsernameIgnoreCase("guest")).thenReturn(Optional.of(customer));
        when(reservations.findById(56L)).thenReturn(Optional.of(reservation));
        when(payments.sumPaidForReservation(56L, PaymentStatus.PAID)).thenReturn(BigDecimal.ZERO);
        when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = service.payNextDue(56L, "guest");

        assertEquals(PaymentType.FULL_PAYMENT, payment.getPaymentType());
        assertEquals(new BigDecimal("2000.00"), payment.getAmount());
        assertEquals(PaymentStatus.PAID, payment.getStatus());
    }

    @Test
    void checkedInCustomerCannotPayRemainingBalance() {
        PaymentRepository payments = mock(PaymentRepository.class);
        PaymentRefundRequestRepository refundRequests = mock(PaymentRefundRequestRepository.class);
        ReservationRepository reservations = mock(ReservationRepository.class);
        SystemUserRepository users = mock(SystemUserRepository.class);
        AuditService audit = mock(AuditService.class);
        NotificationService notifications = mock(NotificationService.class);
        PaymentService service = new PaymentService(payments, refundRequests, reservations, users, audit, "SIMULATED",
                notifications, Clock.fixed(java.time.Instant.parse("2026-11-08T04:00:00Z"), ZoneId.of("Asia/Manila")));

        SystemUser customer = new SystemUser("guest", "guest@example.com", "encoded", AccountRole.CUSTOMER);
        Reservation reservation = new Reservation(null, "guest", "guest@example.com", 4L,
                "R-04", "Deluxe Room", LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 12), 2, 2,
                new BigDecimal("1000.00"), new BigDecimal("2000.00"));
        reservation.changeStatus(ReservationStatus.CHECKED_IN);
        when(users.findByUsernameIgnoreCase("guest")).thenReturn(Optional.of(customer));
        when(reservations.findById(55L)).thenReturn(Optional.of(reservation));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.payNextDue(55L, "guest"));

        assertTrue(error.getMessage().contains("Remaining-balance collection is not supported"));
        verify(payments, never()).save(any(Payment.class));
    }

    @Test
    void refundRequiresAnotherAdminAndIsRecordedOnlyAfterAuthorization() {
        PaymentRepository payments = mock(PaymentRepository.class);
        PaymentRefundRequestRepository refundRequests = mock(PaymentRefundRequestRepository.class);
        ReservationRepository reservations = mock(ReservationRepository.class);
        SystemUserRepository users = mock(SystemUserRepository.class);
        AuditService audit = mock(AuditService.class);
        NotificationService notifications = mock(NotificationService.class);
        Clock clock = Clock.fixed(java.time.Instant.parse("2026-11-08T04:00:00Z"), ZoneId.of("Asia/Manila"));
        PaymentService service = new PaymentService(payments, refundRequests, reservations, users, audit,
                "SIMULATED", notifications, clock);

        Payment payment = new Payment(55L, "guest", PaymentType.FULL_PAYMENT,
                new BigDecimal("1000.00"), "SIMULATED", "SIM-REFUND-TEST", PaymentStatus.PAID);
        ReflectionTestUtils.setField(payment, "id", 20L);
        Reservation reservation = new Reservation(null, "guest", "guest@example.com", 4L,
                "R-04", "Deluxe Room", LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 12), 2, 2,
                new BigDecimal("1000.00"), new BigDecimal("2000.00"));
        when(payments.findById(20L)).thenReturn(Optional.of(payment));
        when(refundRequests.existsByPaymentIdAndStatus(20L, RefundRequestStatus.PENDING)).thenReturn(false);
        when(refundRequests.save(any(PaymentRefundRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservations.findById(55L)).thenReturn(Optional.of(reservation));

        PaymentRefundRequest request = service.requestRefund(20L, "admin-one", "Duplicate payment");
        ReflectionTestUtils.setField(request, "id", 88L);
        when(refundRequests.findById(88L)).thenReturn(Optional.of(request));

        assertThrows(IllegalArgumentException.class,
                () -> service.reviewRefund(88L, true, "admin-one", null));
        assertEquals(PaymentStatus.PAID, payment.getStatus());
        assertEquals(RefundRequestStatus.PENDING, request.getStatus());

        service.reviewRefund(88L, true, "admin-two", null);

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        assertEquals(RefundRequestStatus.APPROVED, request.getStatus());
        assertEquals("admin-two", request.getReviewedBy());
        verify(audit).record(eq("admin-two"), eq("PAYMENT_REFUND_AUTHORIZED"),
                eq("PAYMENT"), eq("20"), contains("admin-one"));
    }
}
