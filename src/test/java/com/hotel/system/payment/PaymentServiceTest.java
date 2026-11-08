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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentServiceTest {
    @Test
    void checkedInCustomerCanPayRemainingBalance() {
        PaymentRepository payments = mock(PaymentRepository.class);
        ReservationRepository reservations = mock(ReservationRepository.class);
        SystemUserRepository users = mock(SystemUserRepository.class);
        AuditService audit = mock(AuditService.class);
        NotificationService notifications = mock(NotificationService.class);
        PaymentService service = new PaymentService(payments, reservations, users, audit, "SIMULATED", notifications);

        SystemUser customer = new SystemUser("guest", "guest@example.com", "encoded", AccountRole.CUSTOMER);
        Reservation reservation = new Reservation(null, "guest", "guest@example.com", 4L,
                "R-04", "Deluxe Room", LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 12), 2, 2,
                new BigDecimal("1000.00"), new BigDecimal("2000.00"));
        reservation.changeStatus(ReservationStatus.CHECKED_IN);

        when(users.findByUsernameIgnoreCase("guest")).thenReturn(Optional.of(customer));
        when(reservations.findById(55L)).thenReturn(Optional.of(reservation));
        when(payments.sumPaidForReservation(55L, PaymentStatus.PAID)).thenReturn(new BigDecimal("1000.00"));
        when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = service.payNextDue(55L, "guest");

        assertEquals(PaymentType.BALANCE, payment.getPaymentType());
        assertEquals(new BigDecimal("1000.00"), payment.getAmount());
        assertEquals(PaymentStatus.PAID, payment.getStatus());
        verify(payments).save(any(Payment.class));
    }
}
