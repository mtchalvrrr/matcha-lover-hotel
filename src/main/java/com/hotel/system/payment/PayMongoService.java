package com.hotel.system.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.system.account.SystemUser;
import com.hotel.system.account.SystemUserRepository;
import com.hotel.system.audit.AuditService;
import com.hotel.system.notification.NotificationService;
import com.hotel.system.reservation.Reservation;
import com.hotel.system.reservation.ReservationRepository;
import com.hotel.system.reservation.ReservationStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class PayMongoService {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final PaymentRepository payments;
    private final ReservationRepository reservations;
    private final SystemUserRepository users;
    private final AuditService audit;
    private final NotificationService notifications;
    private final String mode;
    private final String secretKey;
    private final String webhookSecret;
    private final String publicBaseUrl;
    private final Clock clock;

    public PayMongoService(RestClient.Builder builder, ObjectMapper mapper, PaymentRepository payments,
            ReservationRepository reservations, SystemUserRepository users, AuditService audit,
            NotificationService notifications, @Value("${payment.mode:SIMULATED}") String mode,
            @Value("${payment.paymongo.secret-key:}") String secretKey,
            @Value("${payment.paymongo.webhook-secret:}") String webhookSecret,
            @Value("${hotel.public-base-url:http://localhost:8080}") String publicBaseUrl,
            Clock clock) {
        this.client = builder.baseUrl("https://api.paymongo.com").build();
        this.mapper = mapper;
        this.payments = payments;
        this.reservations = reservations;
        this.users = users;
        this.audit = audit;
        this.notifications = notifications;
        this.mode = mode;
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.clock = clock;
    }

    public String createCheckout(Long reservationId, String username) {
        requireTestConfiguration();
        SystemUser customer = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Customer account was not found."));
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (!Objects.equals(customer.getId(), reservation.getCustomerId()))
            throw new IllegalArgumentException("Reservation not found.");
        if (reservation.getStatus() != ReservationStatus.APPROVED
                && reservation.getStatus() != ReservationStatus.CHECKED_IN)
            throw new IllegalStateException("Payment is available only for approved or checked-in reservations.");

        Optional<Payment> existing = payments.findFirstByReservationIdAndPaymentMethodAndStatusOrderByCreatedAtDesc(
                reservationId, "PAYMONGO", PaymentStatus.PENDING);
        if (existing.isPresent() && existing.get().getCheckoutUrl() != null)
            return existing.get().getCheckoutUrl();

        BigDecimal paid = payments.sumPaidForReservation(reservationId, PaymentStatus.PAID);
        BigDecimal due = reservation.getTotalAmount().subtract(paid).setScale(2, RoundingMode.HALF_UP);
        if (due.signum() <= 0) throw new IllegalStateException("This reservation is already fully paid.");
        PaymentType type = paid.signum() == 0 ? PaymentType.DOWN_PAYMENT : PaymentType.BALANCE;
        BigDecimal amount = paid.signum() == 0
                ? reservation.getTotalAmount().multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP)
                : due;
        String reference = "HOTEL-" + reservationId + "-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        Payment payment = payments.save(new Payment(reservationId, customer.getUsername(), type,
                amount, "PAYMONGO", reference, PaymentStatus.PENDING));

        long amountCentavos = amount.movePointRight(2).longValueExact();
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("line_items", List.of(Map.of(
                "name", "Hotel reservation #" + reservationId + " — " + type.name().replace('_', ' '),
                "amount", amountCentavos, "currency", "PHP", "quantity", 1)));
        attributes.put("payment_method_types", List.of("card", "gcash", "qrph"));
        attributes.put("success_url", publicBaseUrl + "/customer/payments?checkout=returned");
        attributes.put("cancel_url", publicBaseUrl + "/customer/reservations");
        attributes.put("reference_number", reference);
        attributes.put("send_email_receipt", true);
        attributes.put("metadata", Map.of("reservation_id", reservationId.toString(),
                "hotel_payment_id", payment.getId().toString()));
        Map<String, Object> body = Map.of("data", Map.of("attributes", attributes));

        Map<?, ?> response = client.post().uri("/v2/checkout_sessions")
                .headers(headers -> headers.setBasicAuth(secretKey, ""))
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, result) -> {
                    throw new IllegalStateException("PayMongo could not create checkout. Check the API key and payment settings.");
                })
                .body(Map.class);
        if (response == null || !(response.get("data") instanceof Map<?, ?> data)
                || !(data.get("id") instanceof String sessionId)
                || !(data.get("attributes") instanceof Map<?, ?> responseAttributes)
                || !(responseAttributes.get("checkout_url") instanceof String checkoutUrl)) {
            throw new IllegalStateException("PayMongo returned an incomplete checkout response.");
        }
        payment.attachCheckoutSession(sessionId, checkoutUrl);
        audit.record(username, "PAYMONGO_CHECKOUT_CREATED", "PAYMENT", payment.getId().toString(),
                "Hosted checkout created for reservation #" + reservationId + "; reference " + reference);
        return checkoutUrl;
    }

    public void handleWebhook(String rawBody, String signatureHeader) {
        requireTestConfiguration();
        verifySignature(rawBody, signatureHeader);
        try {
            JsonNode root = mapper.readTree(rawBody);
            if (root.path("data").path("livemode").asBoolean(false)) {
                throw new IllegalArgumentException("Live-mode PayMongo events are not accepted by this test integration.");
            }
            JsonNode event = root.path("data");
            String eventType = event.path("type").asText("");
            if (!"checkout_session.payment.paid".equals(eventType)) return;
            JsonNode session = event.path("data");
            String sessionId = session.path("id").asText("");
            JsonNode attributes = session.path("attributes");
            String reference = attributes.path("reference_number").asText("");
            JsonNode paidEntries = attributes.path("payments");
            JsonNode paidEntry = null;
            if (paidEntries.isArray()) {
                for (JsonNode candidate : paidEntries) {
                    if ("paid".equalsIgnoreCase(candidate.path("attributes").path("status").asText())) {
                        paidEntry = candidate;
                        break;
                    }
                }
            }
            if (reference.isBlank() || sessionId.isBlank() || paidEntry == null)
                throw new IllegalArgumentException("The paid checkout webhook is incomplete.");
            JsonNode paidAttributes = paidEntry.path("attributes");
            if (!"PHP".equalsIgnoreCase(paidAttributes.path("currency").asText()))
                throw new IllegalArgumentException("The webhook currency does not match PHP.");
            Payment payment = payments.findByTransactionReference(reference)
                    .orElseThrow(() -> new IllegalArgumentException("No matching hotel payment was found."));
            long providerAmount = paidAttributes.path("amount").asLong(-1);
            long expectedAmount = payment.getAmount().movePointRight(2).longValueExact();
            if (providerAmount != expectedAmount)
                throw new IllegalArgumentException("The provider payment amount does not match the hotel payment.");
            if (payment.getStatus() == PaymentStatus.PAID) return;
            if (!Objects.equals(payment.getProviderCheckoutSessionId(), sessionId))
                throw new IllegalArgumentException("The checkout session does not match the pending hotel payment.");
            String providerPaymentId = paidEntry.path("id").asText("");
            payment.markPaidFromProvider(sessionId, providerPaymentId);
            audit.record("PAYMONGO_WEBHOOK", "PAYMENT_CONFIRMED", "PAYMENT", payment.getId().toString(),
                    "Verified PayMongo checkout payment; provider reference " + providerPaymentId);
            notifications.notifyUser(payment.getCustomerUsername(), "Payment confirmed",
                    "Your PayMongo payment of ₱" + payment.getAmount()
                            + " for reservation #" + payment.getReservationId() + " has been confirmed.", "PAYMENT");
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("The PayMongo webhook payload could not be processed.", ex);
        }
    }

    private void verifySignature(String rawBody, String header) {
        if (rawBody == null || header == null || webhookSecret.isBlank())
            throw new IllegalArgumentException("PayMongo webhook verification is not configured.");
        Map<String, String> parts = new HashMap<>();
        for (String item : header.split(",")) {
            String[] pair = item.trim().split("=", 2);
            if (pair.length == 2) parts.put(pair[0].trim(), pair[1].trim());
        }
        String timestamp = parts.get("t");
        String supplied = parts.get("te");
        if (timestamp == null || supplied == null || supplied.isBlank())
            throw new IllegalArgumentException("PayMongo webhook signature is invalid.");
        long epoch;
        try { epoch = Long.parseLong(timestamp); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("PayMongo webhook timestamp is invalid."); }
        if (Math.abs(Instant.now(clock).getEpochSecond() - epoch) > 300)
            throw new IllegalArgumentException("PayMongo webhook timestamp is outside the allowed time window.");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(digest);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    supplied.getBytes(StandardCharsets.US_ASCII)))
                throw new IllegalArgumentException("PayMongo webhook signature verification failed.");
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("PayMongo webhook verification is unavailable.", ex);
        }
    }

    private void requireTestConfiguration() {
        if (!"PAYMONGO_TEST".equalsIgnoreCase(mode) || !secretKey.startsWith("sk_test_")
                || webhookSecret.isBlank())
            throw new IllegalStateException("PayMongo test mode is not configured. Set PAYMENT_MODE=PAYMONGO_TEST, PAYMONGO_SECRET_KEY, and PAYMONGO_WEBHOOK_SECRET.");
    }
}
