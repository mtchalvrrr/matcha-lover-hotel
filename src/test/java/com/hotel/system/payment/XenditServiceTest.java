package com.hotel.system.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.system.account.SystemUserRepository;
import com.hotel.system.audit.AuditService;
import com.hotel.system.notification.NotificationService;
import com.hotel.system.reservation.Reservation;
import com.hotel.system.reservation.ReservationRepository;
import com.hotel.system.reservation.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class XenditServiceTest {
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final ProcessedXenditEventRepository events = mock(ProcessedXenditEventRepository.class);
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final AuditService audit = mock(AuditService.class);

    private XenditService service() {
        return new XenditService(RestClient.builder(), new ObjectMapper(), payments, events, reservations,
                mock(SystemUserRepository.class), audit, notifications, "XENDIT_TEST",
                "xnd_development_test_key", "webhook-token", "https://hotel.example",
                "Matcha Lover Hotel", Clock.systemUTC());
    }

    private Payment pendingPayment() {
        Payment payment = new Payment(55L, "guest", PaymentType.FULL_PAYMENT,
                new BigDecimal("1500.00"), "XENDIT", "HOTEL-55-TEST", PaymentStatus.PENDING);
        ReflectionTestUtils.setField(payment, "id", 91L);
        payment.attachCheckoutSession("ps-test-session-id-123456789", "https://checkout-staging.xendit.co/session");
        return payment;
    }

    private Reservation pendingReservation() {
        Reservation reservation = new Reservation(4L, "guest", "guest@example.com", 8L,
                "R-08", "Deluxe Room", LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 2), 2, 1,
                new BigDecimal("1500.00"), new BigDecimal("1500.00"));
        ReflectionTestUtils.setField(reservation, "id", 55L);
        reservation.changeStatus(ReservationStatus.PAYMENT_PENDING);
        return reservation;
    }

    @Test
    void rejectsWebhookWithIncorrectVerificationToken() {
        XenditService service = service();
        assertThrows(IllegalArgumentException.class,
                () -> service.handleWebhook("{}".getBytes(), "wrong-token"));
        verifyNoInteractions(events);
    }

    @Test
    void verifiedCompletedSessionMarksPaymentPaidAndSubmitsReservationForManagerReview() throws Exception {
        Payment payment = pendingPayment();
        Reservation reservation = pendingReservation();
        when(events.existsByEventKey(anyString())).thenReturn(false);
        when(payments.findByTransactionReference("HOTEL-55-TEST")).thenReturn(Optional.of(payment));
        when(reservations.findById(55L)).thenReturn(Optional.of(reservation));
        when(events.save(any(ProcessedXenditEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String payload = """
                {
                  "event":"payment_session.completed",
                  "data":{
                    "payment_session_id":"ps-test-session-id-123456789",
                    "reference_id":"HOTEL-55-TEST",
                    "payment_id":"py-test-payment-id",
                    "session_type":"PAY",
                    "status":"COMPLETED",
                    "currency":"PHP",
                    "country":"PH",
                    "amount":1500
                  }
                }
                """;

        service().handleWebhook(payload.getBytes(), "webhook-token");

        assertEquals(PaymentStatus.PAID, payment.getStatus());
        assertEquals("py-test-payment-id", payment.getProviderPaymentId());
        assertEquals(ReservationStatus.PENDING, reservation.getStatus());
        verify(events).save(any(ProcessedXenditEvent.class));
        verify(notifications).notifyUserOnce(eq("guest"), eq("Hotel payment receipt"),
                anyString(), eq("PAYMENT"), anyString());
        verify(notifications).notifyAdminsOnce(eq("New paid reservation request"),
                anyString(), eq("RESERVATION"), anyString());
    }

    @Test
    void rejectsCompletedSessionWithMismatchedAmount() throws Exception {
        Payment payment = pendingPayment();
        when(events.existsByEventKey(anyString())).thenReturn(false);
        when(payments.findByTransactionReference("HOTEL-55-TEST")).thenReturn(Optional.of(payment));

        String payload = """
                {
                  "event":"payment_session.completed",
                  "data":{
                    "payment_session_id":"ps-test-session-id-123456789",
                    "reference_id":"HOTEL-55-TEST",
                    "payment_id":"py-test-payment-id",
                    "session_type":"PAY",
                    "status":"COMPLETED",
                    "currency":"PHP",
                    "country":"PH",
                    "amount":1501
                  }
                }
                """;

        assertThrows(IllegalArgumentException.class,
                () -> service().handleWebhook(payload.getBytes(), "webhook-token"));
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        verify(events, never()).save(any(ProcessedXenditEvent.class));
    }
}
