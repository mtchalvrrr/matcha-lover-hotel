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
    private final ProcessedPayMongoEventRepository processedEvents;
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
            ProcessedPayMongoEventRepository processedEvents,
            ReservationRepository reservations, SystemUserRepository users, AuditService audit,
            NotificationService notifications, @Value("${payment.mode:SIMULATED}") String mode,
            @Value("${payment.paymongo.secret-key:}") String secretKey,
            @Value("${payment.paymongo.webhook-secret:}") String webhookSecret,
            @Value("${hotel.public-base-url:http://localhost:8080}") String publicBaseUrl,
            Clock clock) {
        this.client = builder.baseUrl("https://api.paymongo.com").build();
        this.mapper = mapper;
        this.payments = payments;
        this.processedEvents = processedEvents;
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

        BigDecimal paid = paidAmountForReservation(reservationId);
        BigDecimal due = reservation.getTotalAmount().subtract(paid).setScale(2, RoundingMode.HALF_UP);
        if (due.signum() <= 0) throw new IllegalStateException("This reservation is already fully paid.");
        PaymentType type = paid.signum() == 0 ? PaymentType.DOWN_PAYMENT : PaymentType.BALANCE;
        BigDecimal amount = paid.signum() == 0
                ? reservation.getTotalAmount().multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP)
                : due;
        String reference = "HOTEL-" + reservationId + "-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        // Force the reservation's optimistic version check before creating an external checkout.
        reservation.touchForPayment();
        reservations.saveAndFlush(reservation);
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
                .headers(headers -> {
                    headers.setBasicAuth(secretKey, "");
                    headers.set("Idempotency-Key", "hotel-checkout-" + payment.getTransactionReference());
                })
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
            JsonNode event = root.path("data");
            JsonNode eventAttributes = event.path("attributes");
            String eventId = event.path("id").asText("");
            if (!eventId.isBlank() && processedEvents.existsByEventId(eventId)) return;
            boolean liveMode = eventAttributes.path("livemode").asBoolean(
                    event.path("livemode").asBoolean(false));
            if (liveMode) {
                throw new IllegalArgumentException("Live-mode PayMongo events are not accepted by this test integration.");
            }
            String eventType = eventAttributes.path("type").asText(event.path("type").asText(""));
            if ("payment.refund.updated".equals(eventType) || "payment.refunded".equals(eventType)) {
                processRefundWebhook(eventAttributes.path("data"));
                recordProcessedEvent(eventId, eventType);
                return;
            }
            if ("payment.failed".equals(eventType) || "checkout_session.payment.failed".equals(eventType)) {
                processFailedPaymentWebhook(eventAttributes.path("data"));
                recordProcessedEvent(eventId, eventType);
                return;
            }
            if (!"checkout_session.payment.paid".equals(eventType)) return;
            JsonNode session = eventAttributes.path("data");
            if (session.isMissingNode() || session.isNull()) session = event.path("data");
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
            if (payment.getStatus() == PaymentStatus.PAID) {
                recordProcessedEvent(eventId, eventType);
                return;
            }
            if (!Objects.equals(payment.getProviderCheckoutSessionId(), sessionId))
                throw new IllegalArgumentException("The checkout session does not match the pending hotel payment.");
            String providerPaymentId = paidEntry.path("id").asText("");
            payment.markPaidFromProvider(sessionId, providerPaymentId);
            audit.record("PAYMONGO_WEBHOOK", "PAYMENT_CONFIRMED", "PAYMENT", payment.getId().toString(),
                    "Verified PayMongo checkout payment; provider reference " + providerPaymentId);
            notifications.notifyUserInAppOnly(payment.getCustomerUsername(), "Payment confirmed",
                    "Your PayMongo payment of ₱" + payment.getAmount()
                            + " for reservation #" + payment.getReservationId() + " has been confirmed.", "PAYMENT");
            recordProcessedEvent(eventId, eventType);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("The PayMongo webhook payload could not be processed.", ex);
        }
    }

    private BigDecimal paidAmountForReservation(Long reservationId) {
        BigDecimal paid = payments.sumPaidForReservation(reservationId, PaymentStatus.PAID);
        BigDecimal refundPending = payments.sumPaidForReservation(reservationId, PaymentStatus.REFUND_PENDING);
        return (paid == null ? BigDecimal.ZERO : paid)
                .add(refundPending == null ? BigDecimal.ZERO : refundPending);
    }

    public boolean isConfigured() {
        return "PAYMONGO_TEST".equalsIgnoreCase(mode) && secretKey.startsWith("sk_test_")
                && !webhookSecret.isBlank();
    }

    public void reconcilePendingCheckouts() {
        if (!isConfigured()) return;
        List<Payment> pending = payments.findByPaymentMethodAndStatusAndProviderCheckoutSessionIdIsNotNull(
                "PAYMONGO", PaymentStatus.PENDING);
        for (Payment payment : pending) {
            try {
                Map<?, ?> response = client.get()
                        .uri("/v2/checkout_sessions/{id}", payment.getProviderCheckoutSessionId())
                        .headers(headers -> headers.setBasicAuth(secretKey, ""))
                        .retrieve().body(Map.class);
                if (response == null || !(response.get("data") instanceof Map<?, ?> data)
                        || !(data.get("attributes") instanceof Map<?, ?> attributes)) continue;
                if (Boolean.TRUE.equals(attributes.get("livemode"))) continue;
                String reference = String.valueOf(attributes.get("reference_number"));
                if (!payment.getTransactionReference().equals(reference)) continue;
                Object rawPayments = attributes.get("payments");
                if (rawPayments instanceof List<?> entries) {
                    for (Object entry : entries) {
                        if (!(entry instanceof Map<?, ?> item)
                                || !(item.get("attributes") instanceof Map<?, ?> paidAttributes)) continue;
                        String attemptStatus = String.valueOf(paidAttributes.get("status")).toLowerCase(Locale.ROOT);
                        if ("failed".equals(attemptStatus) || "cancelled".equals(attemptStatus)) {
                            String attemptId = String.valueOf(item.get("id"));
                            notifications.notifyUserOnceInAppOnly(payment.getCustomerUsername(),
                                    "Payment attempt was not completed",
                                    "Your PayMongo payment attempt for reservation #" + payment.getReservationId()
                                            + " was not completed. You can retry using the same checkout page.",
                                    "PAYMENT", "PAYMONGO-ATTEMPT-FAILED-" + payment.getId() + "-" + attemptId);
                            continue;
                        }
                        if (!"paid".equals(attemptStatus)) continue;
                        long amount = Long.parseLong(String.valueOf(paidAttributes.get("amount")));
                        long expected = payment.getAmount().movePointRight(2).longValueExact();
                        if (!"PHP".equalsIgnoreCase(String.valueOf(paidAttributes.get("currency")))
                                || amount != expected) continue;
                        String providerPaymentId = String.valueOf(item.get("id"));
                        payment.markPaidFromProvider(payment.getProviderCheckoutSessionId(), providerPaymentId);
                        audit.record("PAYMONGO_RECONCILIATION", "PAYMENT_CONFIRMED", "PAYMENT",
                                payment.getId().toString(), "Payment confirmed by authenticated PayMongo checkout retrieval.");
                        notifications.notifyUserInAppOnly(payment.getCustomerUsername(), "Payment confirmed",
                                "Your PayMongo payment for reservation #" + payment.getReservationId()
                                        + " has been confirmed.", "PAYMENT");
                        break;
                    }
                }
                if ("expired".equalsIgnoreCase(String.valueOf(attributes.get("status")))
                        && payment.getStatus() == PaymentStatus.PENDING) {
                    payment.markFailedFromProvider(null);
                    audit.record("PAYMONGO_RECONCILIATION", "CHECKOUT_EXPIRED", "PAYMENT",
                            payment.getId().toString(), "PayMongo checkout session expired without a paid transaction.");
                    notifications.notifyUserInAppOnly(payment.getCustomerUsername(), "Payment session expired",
                            "Your payment session for reservation #" + payment.getReservationId()
                                    + " expired without payment. You can start a new payment from your reservations.", "PAYMENT");
                }
            } catch (RuntimeException ex) {
                // A temporary provider/API failure must not change the local payment status.
            }
        }
    }

    public String refund(Payment payment, PaymentRefundRequest refundRequest) {
        requireTestConfiguration();
        if (payment == null || refundRequest == null
                || !"PAYMONGO".equalsIgnoreCase(payment.getPaymentMethod())
                || payment.getProviderPaymentId() == null || payment.getProviderPaymentId().isBlank()) {
            throw new IllegalStateException("This payment has no confirmed PayMongo payment reference to refund.");
        }
        if (refundRequest.getCreatedAt() != null
                && refundRequest.getCreatedAt().isBefore(Instant.now(clock).minusSeconds(23 * 60 * 60))) {
            throw new IllegalStateException("This refund request is older than the provider idempotency window. Check the PayMongo dashboard before retrying to avoid a duplicate refund.");
        }
        String reason = refundRequest.getReason();
        String notes = reason == null ? "Hotel refund approved by a second administrator" : reason.trim();
        if (notes.length() > 255) notes = notes.substring(0, 255);
        Map<String, Object> attributes = Map.of(
                "amount", payment.getAmount().movePointRight(2).longValueExact(),
                "payment_id", payment.getProviderPaymentId(),
                "reason", "requested_by_customer",
                "notes", notes);
        Map<String, Object> body = Map.of("data", Map.of("attributes", attributes));
        Map<?, ?> response = client.post().uri("/v1/refunds")
                .headers(headers -> {
                    headers.setBasicAuth(secretKey, "");
                    headers.set("Idempotency-Key", "hotel-refund-request-" + refundRequest.getId());
                })
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, result) -> {
                    throw new IllegalStateException("PayMongo could not process the refund. The payment remains recorded as paid.");
                })
                .body(Map.class);
        if (response == null || !(response.get("data") instanceof Map<?, ?> data)
                || !(data.get("id") instanceof String refundId)
                || !(data.get("attributes") instanceof Map<?, ?> refundAttributes)) {
            throw new IllegalStateException("PayMongo returned an incomplete refund response.");
        }
        String status = String.valueOf(refundAttributes.get("status")).toLowerCase(Locale.ROOT);
        if ("succeeded".equals(status)) {
            payment.markRefunded(Instant.now(clock));
        } else if ("pending".equals(status) || "processing".equals(status)) {
            payment.markRefundPending(refundId, status);
        } else {
            throw new IllegalStateException("PayMongo did not accept the refund. The payment remains recorded as paid.");
        }
        audit.record("PAYMONGO", "PAYMONGO_REFUND_" + status.toUpperCase(Locale.ROOT),
                "PAYMENT", payment.getId().toString(), "Provider refund " + refundId + " status " + status);
        return status;
    }

    private void recordProcessedEvent(String eventId, String eventType) {
        if (eventId != null && !eventId.isBlank())
            processedEvents.save(new ProcessedPayMongoEvent(eventId, eventType));
    }

    private void processFailedPaymentWebhook(JsonNode resource) {
        JsonNode attributes = resource.path("attributes");
        String reference = attributes.path("reference_number").asText("");
        String providerPaymentId = resource.path("id").asText("");
        Payment payment = reference.isBlank() ? null
                : payments.findByTransactionReference(reference).orElse(null);
        if (payment == null) {
            String paymentId = attributes.path("metadata").path("hotel_payment_id").asText("");
            if (!paymentId.isBlank()) {
                try { payment = payments.findById(Long.parseLong(paymentId)).orElse(null); }
                catch (NumberFormatException ignored) { return; }
            }
        }
        if (payment == null || payment.getStatus() != PaymentStatus.PENDING) return;
        payment.markFailedFromProvider(providerPaymentId);
        audit.record("PAYMONGO_WEBHOOK", "PAYMENT_FAILED", "PAYMENT", payment.getId().toString(),
                "PayMongo reported a failed checkout/payment attempt.");
        notifications.notifyUserInAppOnly(payment.getCustomerUsername(), "Payment was not completed",
                "Your payment attempt for reservation #" + payment.getReservationId()
                        + " was not completed. You can try again from your reservations page.", "PAYMENT");
    }

    private void processRefundWebhook(JsonNode resource) {
        JsonNode attributes = resource.path("attributes");
        String providerPaymentId = attributes.path("payment_id").asText("");
        String refundId = resource.path("id").asText("");
        String status = attributes.path("status").asText("").toLowerCase(Locale.ROOT);
        if (providerPaymentId.isBlank() && attributes.path("refunds").isArray()
                && !attributes.path("refunds").isEmpty()) {
            JsonNode refund = attributes.path("refunds").get(0);
            providerPaymentId = refund.path("attributes").path("payment_id").asText("");
            refundId = refund.path("id").asText(refundId);
            status = refund.path("attributes").path("status").asText(status).toLowerCase(Locale.ROOT);
        }
        if (providerPaymentId.isBlank()) return;
        Payment payment = payments.findByProviderPaymentId(providerPaymentId).orElse(null);
        if (payment == null) return;
        if ("succeeded".equals(status)) {
            if (payment.getStatus() != PaymentStatus.REFUNDED) payment.markRefunded(Instant.now(clock));
        } else if ("failed".equals(status)) {
            payment.markRefundFailed(refundId);
        } else if ("pending".equals(status) || "processing".equals(status)) {
            payment.markRefundPending(refundId, status);
        }
        audit.record("PAYMONGO_WEBHOOK", "PAYMONGO_REFUND_STATUS_" + status.toUpperCase(Locale.ROOT),
                "PAYMENT", payment.getId().toString(), "Provider refund " + refundId + " status " + status);
        if ("succeeded".equals(status)) {
            notifications.notifyUserInAppOnly(payment.getCustomerUsername(), "Refund completed",
                    "Your refund for payment " + payment.getTransactionReference() + " has been processed by PayMongo.",
                    "PAYMENT");
        } else if ("failed".equals(status)) {
            notifications.notifyUserInAppOnly(payment.getCustomerUsername(), "Refund failed",
                    "The payment provider could not complete your refund. Please contact the hotel.",
                    "PAYMENT");
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
