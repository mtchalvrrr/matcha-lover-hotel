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
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class NexusPayService {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final PaymentRepository payments;
    private final ProcessedPaymentEventRepository processedEvents;
    private final ReservationRepository reservations;
    private final SystemUserRepository users;
    private final AuditService audit;
    private final NotificationService notifications;
    private final String secretKey;
    private final String webhookSecret;
    private final String publicBaseUrl;
    private final String hotelName;
    private final String method;
    private final Clock clock;

    public NexusPayService(RestClient.Builder builder, ObjectMapper mapper, PaymentRepository payments,
            ProcessedPaymentEventRepository processedEvents, ReservationRepository reservations,
            SystemUserRepository users, AuditService audit, NotificationService notifications,
            @Value("${payment.nexuspay.secret-key:}") String secretKey,
            @Value("${payment.nexuspay.webhook-secret:}") String webhookSecret,
            @Value("${hotel.public-base-url:http://localhost:8080}") String publicBaseUrl,
            @Value("${hotel.name:[HOTEL NAME]}") String hotelName,
            @Value("${payment.nexuspay.method:gcash_qr}") String method, Clock clock) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        this.client = builder.requestFactory(requestFactory).baseUrl("https://nexuspayph.com").build();
        this.mapper = mapper;
        this.payments = payments;
        this.processedEvents = processedEvents;
        this.reservations = reservations;
        this.users = users;
        this.audit = audit;
        this.notifications = notifications;
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
        this.hotelName = hotelName;
        this.method = method == null ? "gcash_qr" : method.trim();
        this.clock = clock;
    }

    public boolean isConfigured() {
        return secretKey.startsWith("sk_test_") && !method.isBlank()
                && publicBaseUrl.startsWith("https://");
    }

    public String createCheckout(Long reservationId, String username) {
        requireApiConfiguration();
        SystemUser customer = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Customer account was not found."));
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (!Objects.equals(customer.getId(), reservation.getCustomerId())) {
            throw new IllegalArgumentException("Reservation not found.");
        }
        if (reservation.getStatus() != ReservationStatus.PAYMENT_PENDING) {
            throw new IllegalStateException("Full payment is required during the initial reservation checkout. Balance collection is not supported.");
        }

        Optional<Payment> existing = payments.findFirstByReservationIdAndPaymentMethodAndStatusOrderByCreatedAtDesc(
                reservationId, "NEXUSPAY", PaymentStatus.PENDING);
        if (paidAmountForReservation(reservationId).signum() > 0) {
            throw new IllegalStateException("This reservation already has a recorded payment. A second standard reservation payment is not allowed.");
        }
        BigDecimal due = reservation.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
        if (due.signum() <= 0) {
            throw new IllegalStateException("The reservation total must be greater than zero.");
        }

        if (existing.isPresent()) {
            Payment prior = existing.get();
            if (prior.getCheckoutUrl() != null && prior.getAmount().compareTo(due) == 0) {
                return prior.getCheckoutUrl();
            }
            if (prior.getProviderCheckoutSessionId() != null) {
                throw new IllegalStateException("A previous NexusPay checkout is still active. Complete it or wait for it to expire before trying again.");
            }
            prior.markFailedFromProvider(null);
        }

        String reference = "HOTEL-" + reservationId + "-"
                + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        reservation.touchForPayment();
        reservations.saveAndFlush(reservation);
        Payment payment = payments.save(new Payment(reservationId, customer.getUsername(),
                PaymentType.FULL_PAYMENT, due, "NEXUSPAY", reference, PaymentStatus.PENDING));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", due);
        body.put("currency", "PHP");
        body.put("method", method);
        body.put("description", "Hotel reservation #" + reservationId + " - full payment");
        body.put("return_url", publicBaseUrl + "/customer/payments?checkout=returned");
        body.put("customer", Map.of(
                "email", customer.getEmail(),
                "name", reservation.getGuestName()));
        body.put("metadata", Map.of(
                "reservation_id", reservationId.toString(),
                "hotel_payment_id", payment.getId().toString(),
                "hotel_reference", reference));

        Map<?, ?> response;
        try {
            response = client.post().uri("/v1/payments")
                    .headers(headers -> headers.setBearerAuth(secretKey))
                    .header("Idempotency-Key", "hotel-reservation-payment-" + payment.getId())
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException("NexusPay rejected the sandbox checkout request (HTTP "
                    + ex.getStatusCode().value() + "). Check the sandbox API key and configured payment method.", ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("NexusPay could not be reached. No payment was confirmed; please try again.", ex);
        }

        if (response == null) {
            throw new IllegalStateException("NexusPay returned an empty checkout response.");
        }
        String providerPaymentId = Objects.toString(response.get("id"), "");
        String checkoutUrl = Objects.toString(response.get("checkout_url"), "");
        String environment = Objects.toString(response.get("environment"), "test");
        BigDecimal returnedAmount = response.get("amount") instanceof Number number
                ? new BigDecimal(number.toString()) : BigDecimal.ZERO;
        if (providerPaymentId.isBlank() || !providerPaymentId.startsWith("pay_")
                || !checkoutUrl.startsWith("https://")
                || !"test".equalsIgnoreCase(environment)
                || returnedAmount.compareTo(due) != 0
                || !"PHP".equalsIgnoreCase(Objects.toString(response.get("currency"), ""))) {
            throw new IllegalStateException("NexusPay returned an incomplete or mismatched sandbox payment response.");
        }

        payment.attachCheckoutSession(providerPaymentId, checkoutUrl);
        audit.record(username, "NEXUSPAY_CHECKOUT_CREATED", "PAYMENT", payment.getId().toString(),
                "NexusPay sandbox checkout created for reservation #" + reservationId
                        + "; provider payment " + providerPaymentId);
        return checkoutUrl;
    }

    public void handleWebhook(byte[] rawBody, String signatureHeader) {
        requireWebhookConfiguration();
        if (!verifySignature(rawBody, signatureHeader)) {
            throw new IllegalArgumentException("Invalid NexusPay webhook signature.");
        }

        JsonNode root;
        try {
            root = mapper.readTree(rawBody);
        } catch (Exception ex) {
            throw new IllegalArgumentException("The NexusPay webhook payload is not valid JSON.", ex);
        }

        if (!"test".equalsIgnoreCase(root.path("environment").asText(""))) {
            throw new IllegalArgumentException("Only NexusPay sandbox webhook events are accepted.");
        }
        String eventId = root.path("id").asText("");
        String eventType = root.path("type").asText("");
        JsonNode data = root.path("data").path("object");
        if (eventId.isBlank() || eventType.isBlank() || data.isMissingNode() || !data.isObject()) {
            throw new IllegalArgumentException("The NexusPay webhook event is incomplete.");
        }
        if (processedEvents.existsByEventKey(eventId)) {
            return;
        }

        switch (eventType) {
            case "payment.succeeded", "payment.failed", "payment.expired" ->
                    processPaymentState(data, eventId, eventType);
            case "refund.succeeded", "refund.failed" ->
                    processRefundState(data, eventId, eventType);
            default -> {
                // Other NexusPay events do not affect hotel reservation payment state.
                processedEvents.save(new ProcessedPaymentEvent(eventId, eventType));
            }
        }
    }

    public void reconcilePendingCheckouts() {
        if (!isConfigured()) return;
        List<Payment> pending = payments.findByPaymentMethodAndStatusAndProviderCheckoutSessionIdIsNotNull(
                "NEXUSPAY", PaymentStatus.PENDING);
        for (Payment payment : pending) {
            try {
                Map<?, ?> response = client.get().uri("/v1/payments/{id}", payment.getProviderCheckoutSessionId())
                        .headers(headers -> headers.setBearerAuth(secretKey))
                        .retrieve().body(Map.class);
                if (response == null) continue;
                JsonNode providerPayment = mapper.valueToTree(response);
                validateProviderPayment(payment, providerPayment);
                String status = providerPayment.path("status").asText("");
                String eventType = switch (status) {
                    case "succeeded" -> "payment.succeeded";
                    case "failed" -> "payment.failed";
                    case "expired" -> "payment.expired";
                    default -> "";
                };
                if (!eventType.isBlank()) {
                    String eventKey = "reconcile:" + payment.getProviderCheckoutSessionId() + ":" + status;
                    if (!processedEvents.existsByEventKey(eventKey)) {
                        processPaymentState(providerPayment, eventKey, eventType);
                    }
                }
            } catch (RuntimeException ex) {
                // Keep unresolved payments pending. A later poll or signed webhook can resolve them.
            }
        }
    }

    private void processPaymentState(JsonNode data, String eventId, String eventType) {
        String providerPaymentId = data.path("id").asText("");
        String status = data.path("status").asText("");
        if (providerPaymentId.isBlank() || !providerPaymentId.startsWith("pay_")) {
            throw new IllegalArgumentException("The NexusPay payment event has no valid payment ID.");
        }
        Payment payment = payments.findByProviderCheckoutSessionId(providerPaymentId)
                .orElseThrow(() -> new IllegalArgumentException("No matching hotel payment was found."));
        validateProviderPayment(payment, data);

        if ("payment.succeeded".equals(eventType)) {
            if (!"succeeded".equals(status)) {
                throw new IllegalArgumentException("The NexusPay success event has an invalid payment status.");
            }
            if (payment.getStatus() != PaymentStatus.PAID) {
                payment.markPaidFromProvider(providerPaymentId, providerPaymentId);
                audit.record("NEXUSPAY_WEBHOOK", "PAYMENT_CONFIRMED", "PAYMENT", payment.getId().toString(),
                        "NexusPay sandbox confirmed full payment; provider payment ID " + providerPaymentId);
                submitPaidHoldToManagerReview(payment);
                sendPaymentReceipt(payment);
                notifyIfReservationAmountChanged(payment);
            }
        } else {
            boolean expired = "payment.expired".equals(eventType);
            if ((expired && !"expired".equals(status)) || (!expired && !"failed".equals(status))) {
                throw new IllegalArgumentException("The NexusPay failure event has an invalid payment status.");
            }
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.markFailedFromProvider(providerPaymentId);
                Reservation reservation = reservations.findById(payment.getReservationId()).orElse(null);
                if (reservation != null && reservation.getStatus() == ReservationStatus.PAYMENT_PENDING) {
                    reservation.changeStatus(ReservationStatus.EXPIRED);
                    audit.record("NEXUSPAY_WEBHOOK", "UNPAID_RESERVATION_HOLD_EXPIRED", "RESERVATION",
                            reservation.getId().toString(), "Unpaid reservation hold released after NexusPay checkout ended.");
                }
                notifications.notifyUserInAppOnly(payment.getCustomerUsername(),
                        expired ? "Payment session expired" : "Payment was not completed",
                        "Your checkout for reservation #" + payment.getReservationId()
                                + " ended without payment. You can start a new reservation if the room is still available.",
                        "PAYMENT");
            }
        }
        processedEvents.save(new ProcessedPaymentEvent(eventId, eventType));
    }

    private void validateProviderPayment(Payment payment, JsonNode data) {
        String id = data.path("id").asText("");
        if (!id.equals(payment.getProviderCheckoutSessionId())
                || !"test".equalsIgnoreCase(data.path("environment").asText("test"))
                || !"PHP".equalsIgnoreCase(data.path("currency").asText(""))
                || data.path("amount").decimalValue().compareTo(payment.getAmount()) != 0) {
            throw new IllegalArgumentException("The NexusPay payment ID, environment, amount, or currency does not match the hotel payment.");
        }
        JsonNode metadata = data.path("metadata");
        String hotelPaymentId = metadata.path("hotel_payment_id").asText("");
        String reservationId = metadata.path("reservation_id").asText("");
        if ((!hotelPaymentId.isBlank() && !hotelPaymentId.equals(payment.getId().toString()))
                || (!reservationId.isBlank() && !reservationId.equals(payment.getReservationId().toString()))) {
            throw new IllegalArgumentException("The NexusPay metadata does not match the hotel payment.");
        }
    }

    private void processRefundState(JsonNode data, String eventId, String eventType) {
        String refundId = data.path("id").asText("");
        String providerPaymentId = data.path("payment").asText("");
        String status = data.path("status").asText("");
        if (refundId.isBlank() || providerPaymentId.isBlank()
                || !("succeeded".equals(status) || "failed".equals(status))) {
            throw new IllegalArgumentException("The NexusPay refund event is incomplete.");
        }
        Payment payment = payments.findByProviderPaymentId(providerPaymentId)
                .orElseThrow(() -> new IllegalArgumentException("No matching hotel payment was found for this refund."));
        if (data.path("amount").decimalValue().compareTo(payment.getAmount()) != 0
                || (payment.getProviderRefundId() != null && !payment.getProviderRefundId().equals(refundId))) {
            throw new IllegalArgumentException("The NexusPay refund amount or reference does not match the hotel payment.");
        }
        if ("refund.succeeded".equals(eventType) && "succeeded".equals(status)
                && payment.getStatus() == PaymentStatus.REFUND_PENDING) {
            payment.markRefunded(Instant.now(clock), refundId);
            notifyRefundSucceeded(payment, refundId);
        } else if ("refund.failed".equals(eventType) && "failed".equals(status)
                && payment.getStatus() == PaymentStatus.REFUND_PENDING) {
            payment.markRefundFailed(refundId);
            notifications.notifyUser(payment.getCustomerUsername(), "Refund processing failed",
                    "NexusPay could not complete the refund for payment " + payment.getTransactionReference()
                            + ". The hotel administrators have been notified for review.", "PAYMENT");
            notifications.notifyAdmins("NexusPay refund failed",
                    "Refund " + refundId + " failed for payment #" + payment.getId() + ". Review it in NexusPay.",
                    "PAYMENT");
        }
        processedEvents.save(new ProcessedPaymentEvent(eventId, eventType));
    }

    private boolean verifySignature(byte[] rawBody, String signatureHeader) {
        if (signatureHeader == null || rawBody == null) return false;
        try {
            String timestamp = null;
            String suppliedHex = null;
            for (String part : signatureHeader.split(",")) {
                String[] pair = part.trim().split("=", 2);
                if (pair.length != 2) return false;
                if ("t".equals(pair[0])) timestamp = pair[1];
                if ("v1".equals(pair[0])) suppliedHex = pair[1];
            }
            if (timestamp == null || suppliedHex == null) return false;
            long seconds = Long.parseLong(timestamp);
            if (Math.abs(Instant.now(clock).getEpochSecond() - seconds) > 300) return false;

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            byte[] expected = mac.doFinal(rawBody);
            byte[] supplied = HexFormat.of().parseHex(suppliedHex);
            return MessageDigest.isEqual(expected, supplied);
        } catch (Exception ex) {
            return false;
        }
    }

    public void refund(Payment payment, PaymentRefundRequest request) {
        requireApiConfiguration();
        if (payment == null || request == null || !"NEXUSPAY".equalsIgnoreCase(payment.getPaymentMethod())
                || payment.getProviderPaymentId() == null || payment.getProviderPaymentId().isBlank()) {
            throw new IllegalStateException("This payment has no confirmed NexusPay payment reference to refund.");
        }
        String reason = request.getReason() == null ? "" : request.getReason().toLowerCase(Locale.ROOT);
        String providerReason = reason.contains("fraud") ? "fraudulent"
                : reason.contains("duplicate") ? "duplicate"
                : reason.contains("customer") || reason.contains("cancel") ? "requested_by_customer" : "other";
        Map<String, Object> body = Map.of("payment", payment.getProviderPaymentId(), "reason", providerReason);
        Map<?, ?> response;
        try {
            response = client.post().uri("/v1/refunds")
                    .headers(headers -> headers.setBearerAuth(secretKey))
                    .header("Idempotency-Key", "hotel-refund-request-" + request.getId())
                    .body(body).retrieve().body(Map.class);
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException("NexusPay could not start the refund (HTTP "
                    + ex.getStatusCode().value() + "). Check the NexusPay dashboard before retrying.", ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("NexusPay could not be reached to start the refund. Check the provider dashboard before retrying.", ex);
        }
        if (response == null) throw new IllegalStateException("NexusPay returned an empty refund response.");
        String refundId = Objects.toString(response.get("id"), "");
        String status = Objects.toString(response.get("status"), "").toLowerCase(Locale.ROOT);
        BigDecimal amount = response.get("amount") instanceof Number number
                ? new BigDecimal(number.toString()) : BigDecimal.ZERO;
        if (refundId.isBlank() || amount.compareTo(payment.getAmount()) != 0) {
            throw new IllegalStateException("NexusPay returned an incomplete or non-full refund response.");
        }
        payment.markRefundPending(refundId, status);
        if ("succeeded".equals(status)) {
            payment.markRefunded(Instant.now(clock), refundId);
            notifyRefundSucceeded(payment, refundId);
        } else if ("failed".equals(status)) {
            payment.markRefundFailed(refundId);
            notifications.notifyAdmins("NexusPay refund failed",
                    "NexusPay rejected the refund for payment #" + payment.getId()
                            + ". Review the provider dashboard.", "PAYMENT");
        }
    }

    private void notifyRefundSucceeded(Payment payment, String refundId) {
        audit.record("NEXUSPAY_WEBHOOK", "REFUND_CONFIRMED", "PAYMENT", payment.getId().toString(),
                "NexusPay confirmed the full refund.");
        notifications.notifyUserOnce(payment.getCustomerUsername(), "Hotel refund receipt",
                hotelName + "\nYour refund has been confirmed by NexusPay.\n\nPayment reference: "
                        + payment.getTransactionReference() + "\nRefund reference: " + refundId
                        + "\nRefund amount: PHP " + payment.getAmount() + "\nRefund confirmed: "
                        + Instant.now(clock),
                "PAYMENT", "NEXUSPAY-REFUND-RECEIPT-" + payment.getId());
    }

    private void submitPaidHoldToManagerReview(Payment payment) {
        Reservation reservation = reservations.findById(payment.getReservationId()).orElse(null);
        if (reservation == null || reservation.getStatus() != ReservationStatus.PAYMENT_PENDING) return;
        reservation.changeStatus(ReservationStatus.PENDING);
        audit.record("NEXUSPAY_WEBHOOK", "RESERVATION_SUBMITTED_AFTER_FULL_PAYMENT", "RESERVATION",
                reservation.getId().toString(), "Full payment verified. Reservation is now pending manager review.");
        notifications.notifyAdminsOnce("New paid reservation request",
                "Reservation #" + reservation.getId() + " from " + reservation.getCustomerUsername()
                        + " has been fully paid and is awaiting manager review.",
                "RESERVATION", "NEXUSPAY-PAID-RESERVATION-" + reservation.getId());
    }

    private void sendPaymentReceipt(Payment payment) {
        Reservation reservation = reservations.findById(payment.getReservationId()).orElse(null);
        StringBuilder receipt = new StringBuilder().append(hotelName)
                .append("\nYour payment has been confirmed. This email is your hotel payment receipt.\n\n")
                .append("Receipt number: PAY-").append(payment.getId()).append("\n")
                .append("Payment reference: ").append(payment.getTransactionReference()).append("\n")
                .append("Payment method: NEXUSPAY").append("\n")
                .append("Amount paid: PHP ").append(payment.getAmount()).append("\n")
                .append("Payment confirmed: ").append(Instant.now(clock)).append("\n");
        if (reservation != null) {
            receipt.append("\nReservation number: ").append(reservation.getId())
                    .append("\nGuest: ").append(reservation.getGuestName())
                    .append("\nRoom: ").append(reservation.getRoomCode()).append(" — ").append(reservation.getRoomName())
                    .append("\nCheck-in: ").append(reservation.getCheckInDate())
                    .append("\nCheck-out: ").append(reservation.getCheckOutDate())
                    .append("\nReservation total: PHP ").append(reservation.getTotalAmount()).append("\n");
        }
        receipt.append("\nPayment confirmation does not by itself mean the manager has approved the reservation.");
        notifications.notifyUserOnce(payment.getCustomerUsername(), "Hotel payment receipt", receipt.toString(),
                "PAYMENT", "NEXUSPAY-PAYMENT-RECEIPT-" + payment.getId());
    }

    private void notifyIfReservationAmountChanged(Payment payment) {
        Reservation linkedReservation = reservations.findById(payment.getReservationId()).orElse(null);
        if (linkedReservation == null
                || (linkedReservation.getStatus() != ReservationStatus.PENDING
                    && linkedReservation.getStatus() != ReservationStatus.APPROVED
                    && linkedReservation.getStatus() != ReservationStatus.CHECKED_IN)) {
            notifications.notifyAdminsInAppOnly("Payment received for inactive reservation",
                    "NexusPay confirmed payment #" + payment.getId()
                            + " for a reservation that is no longer active. Review and refund if required.",
                    "PAYMENT", "NEXUSPAY-INACTIVE-RESERVATION-" + payment.getId());
            return;
        }
        BigDecimal paidIncludingThis = paidAmountForReservation(payment.getReservationId());
        BigDecimal paidBefore = paidIncludingThis.subtract(payment.getAmount()).max(BigDecimal.ZERO);
        BigDecimal expectedAmount = linkedReservation.getTotalAmount().subtract(paidBefore)
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        if (expectedAmount.compareTo(payment.getAmount()) != 0) {
            audit.record("NEXUSPAY_WEBHOOK", "PAYMENT_AMOUNT_STALE", "PAYMENT", payment.getId().toString(),
                    "The provider payment succeeded but the reservation amount changed after checkout creation.");
            notifications.notifyAdminsInAppOnly("Payment needs reservation reconciliation",
                    "NexusPay confirmed payment #" + payment.getId()
                            + " but the reservation total changed after checkout creation. Review the payment and reservation.",
                    "PAYMENT", "NEXUSPAY-STALE-AMOUNT-" + payment.getId());
        }
    }

    private BigDecimal paidAmountForReservation(Long reservationId) {
        BigDecimal paid = payments.sumPaidForReservation(reservationId, PaymentStatus.PAID);
        BigDecimal pendingRefund = payments.sumPaidForReservation(reservationId, PaymentStatus.REFUND_PENDING);
        return (paid == null ? BigDecimal.ZERO : paid)
                .add(pendingRefund == null ? BigDecimal.ZERO : pendingRefund);
    }

    private void requireApiConfiguration() {
        if (!secretKey.startsWith("sk_test_")) {
            throw new IllegalStateException("NexusPay Sandbox is not configured with an sk_test_ API key. No payment was taken.");
        }
        if (!publicBaseUrl.startsWith("https://")) {
            throw new IllegalStateException("Set HOTEL_PUBLIC_BASE_URL to the public HTTPS Codespaces URL before starting NexusPay checkout.");
        }
        if (method.isBlank()) {
            throw new IllegalStateException("Set NEXUSPAY_METHOD to a payment method enabled for your NexusPay sandbox account.");
        }
    }

    private void requireWebhookConfiguration() {
        if (!secretKey.startsWith("sk_test_") || webhookSecret.isBlank()) {
            throw new IllegalStateException("NexusPay sandbox webhook verification is not configured.");
        }
    }
}
