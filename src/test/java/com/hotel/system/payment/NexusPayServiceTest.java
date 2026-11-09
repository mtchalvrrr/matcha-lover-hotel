package com.hotel.system.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NexusPayServiceTest {
    private static final String WEBHOOK_SECRET = "whsec_test_signing_secret";
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1_800_000_000L), ZoneOffset.UTC);

    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final ProcessedPaymentEventRepository events = mock(ProcessedPaymentEventRepository.class);
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final AuditService audit = mock(AuditService.class);

    private NexusPayService service() {
        return new NexusPayService(RestClient.builder(), new ObjectMapper(), payments, events, reservations,
                mock(SystemUserRepository.class), audit, notifications,
                "sk_test_unit_test_key", WEBHOOK_SECRET, "https://hotel.example",
                "Matcha Lover Hotel", "gcash_qr", CLOCK);
    }

    private Payment pendingPayment() {
        Payment payment = new Payment(55L, "guest", PaymentType.FULL_PAYMENT,
                new BigDecimal("1500.00"), "NEXUSPAY", "HOTEL-55-TEST", PaymentStatus.PENDING);
        ReflectionTestUtils.setField(payment, "id", 91L);
        payment.attachCheckoutSession("pay_test_payment_123456", "https://nexuspayph.com/sandbox/checkout/pay_test_payment_123456");
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

    private String signedHeader(String payload) throws Exception {
        String timestamp = Long.toString(CLOCK.instant().getEpochSecond());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
        String signature = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        return "t=" + timestamp + ",v1=" + signature;
    }

    @Test
    void rejectsWebhookWithIncorrectSignature() {
        assertThrows(IllegalArgumentException.class,
                () -> service().handleWebhook("{}".getBytes(StandardCharsets.UTF_8), "t=1800000000,v1=00"));
        verifyNoInteractions(events);
    }

    @Test
    void verifiedSuccessMarksPaymentPaidAndSubmitsReservationForManagerReview() throws Exception {
        Payment payment = pendingPayment();
        Reservation reservation = pendingReservation();
        when(events.existsByEventKey("evt_test_success_1")).thenReturn(false);
        when(payments.findByProviderCheckoutSessionId("pay_test_payment_123456")).thenReturn(Optional.of(payment));
        when(reservations.findById(55L)).thenReturn(Optional.of(reservation));
        when(events.save(any(ProcessedPaymentEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String payload = """
                {
                  "id":"evt_test_success_1",
                  "type":"payment.succeeded",
                  "environment":"test",
                  "created":1800000000,
                  "data":{"object":{
                    "id":"pay_test_payment_123456",
                    "status":"succeeded",
                    "environment":"test",
                    "amount":1500.00,
                    "currency":"PHP",
                    "metadata":{"hotel_payment_id":"91","reservation_id":"55"}
                  }}
                }
                """;

        service().handleWebhook(payload.getBytes(StandardCharsets.UTF_8), signedHeader(payload));

        assertEquals(PaymentStatus.PAID, payment.getStatus());
        assertEquals("pay_test_payment_123456", payment.getProviderPaymentId());
        assertEquals(ReservationStatus.PENDING, reservation.getStatus());
        verify(events).save(any(ProcessedPaymentEvent.class));
        verify(notifications).notifyUserOnce(eq("guest"), eq("Hotel payment receipt"),
                anyString(), eq("PAYMENT"), anyString());
        verify(notifications).notifyAdminsOnce(eq("New paid reservation request"),
                anyString(), eq("RESERVATION"), anyString());
    }

    @Test
    void rejectsSuccessEventWithMismatchedAmount() throws Exception {
        Payment payment = pendingPayment();
        when(events.existsByEventKey("evt_test_mismatch_1")).thenReturn(false);
        when(payments.findByProviderCheckoutSessionId("pay_test_payment_123456")).thenReturn(Optional.of(payment));

        String payload = """
                {
                  "id":"evt_test_mismatch_1",
                  "type":"payment.succeeded",
                  "environment":"test",
                  "data":{"object":{
                    "id":"pay_test_payment_123456",
                    "status":"succeeded",
                    "environment":"test",
                    "amount":1501.00,
                    "currency":"PHP"
                  }}
                }
                """;

        assertThrows(IllegalArgumentException.class,
                () -> service().handleWebhook(payload.getBytes(StandardCharsets.UTF_8), signedHeader(payload)));
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        verify(events, never()).save(any(ProcessedPaymentEvent.class));
    }

    @Test
    void duplicateWebhookIsIgnored() throws Exception {
        when(events.existsByEventKey("evt_test_duplicate")).thenReturn(true);
        String payload = """
                {"id":"evt_test_duplicate","type":"payment.succeeded","environment":"test",
                 "data":{"object":{"id":"pay_test_payment_123456","status":"succeeded"}}}
                """;
        service().handleWebhook(payload.getBytes(StandardCharsets.UTF_8), signedHeader(payload));
        verifyNoInteractions(payments);
    }
}
