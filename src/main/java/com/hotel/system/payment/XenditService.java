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
import com.hotel.system.reservation.ReservationExtensionRequest;
import com.hotel.system.reservation.ReservationExtensionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class XenditService {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final PaymentRepository payments;
    private final ProcessedXenditEventRepository processedEvents;
    private final ReservationRepository reservations;
    private final ReservationExtensionService extensions;
    private final SystemUserRepository users;
    private final AuditService audit;
    private final NotificationService notifications;
    private final String mode;
    private final String secretKey;
    private final String webhookToken;
    private final String publicBaseUrl;
    private final String hotelName;
    private final Clock clock;

    public XenditService(RestClient.Builder builder, ObjectMapper mapper, PaymentRepository payments,
            ProcessedXenditEventRepository processedEvents, ReservationRepository reservations,
            ReservationExtensionService extensions, SystemUserRepository users, AuditService audit, NotificationService notifications,
            @Value("${payment.mode:SIMULATED}") String mode,
            @Value("${payment.xendit.secret-key:}") String secretKey,
            @Value("${payment.xendit.webhook-token:}") String webhookToken,
            @Value("${hotel.public-base-url:http://localhost:8080}") String publicBaseUrl,
            @Value("${hotel.name:[HOTEL NAME]}") String hotelName, Clock clock) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        this.client = builder.requestFactory(requestFactory)
                .baseUrl("https://api.xendit.co")
                .build();
        this.mapper = mapper;
        this.payments = payments;
        this.processedEvents = processedEvents;
        this.reservations = reservations;
        this.extensions = extensions;
        this.users = users;
        this.audit = audit;
        this.notifications = notifications;
        this.mode = mode;
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.webhookToken = webhookToken == null ? "" : webhookToken.trim();
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.hotelName = hotelName;
        this.clock = clock;
    }

    public boolean isConfigured() {
        return "XENDIT_TEST".equalsIgnoreCase(mode)
                && secretKey.startsWith("xnd_development_")
                && !webhookToken.isBlank();
    }

    public String createCheckout(Long reservationId, String username) {
        requireTestConfiguration();
        SystemUser customer = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Customer account was not found."));
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (!Objects.equals(customer.getId(), reservation.getCustomerId()))
            throw new IllegalArgumentException("Reservation not found.");

        // The system requires 100% payment before Admin review. Standard reservation
        // checkout is therefore available only while the reservation is PAYMENT_PENDING.
        // Additional charges (such as an approved extension) must use their own payment flow.
        if (reservation.getStatus() != ReservationStatus.PAYMENT_PENDING)
            throw new IllegalStateException("Full payment is required during the initial reservation checkout. Balance collection is not supported.");

        Optional<Payment> existing = payments.findFirstByReservationIdAndPaymentMethodAndStatusOrderByCreatedAtDesc(
                reservationId, "XENDIT", PaymentStatus.PENDING);
        BigDecimal paid = paidAmountForReservation(reservationId);
        if (paid.signum() > 0)
            throw new IllegalStateException("This reservation already has a recorded payment. A second standard reservation payment is not allowed.");
        BigDecimal due = reservation.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
        if (due.signum() <= 0) throw new IllegalStateException("The reservation total must be greater than zero.");

        if (existing.isPresent()) {
            Payment prior = existing.get();
            if (prior.getCheckoutUrl() != null && prior.getAmount().compareTo(due) == 0)
                return prior.getCheckoutUrl();
            if (prior.getProviderCheckoutSessionId() != null)
                throw new IllegalStateException("A previous Xendit checkout is still active. Wait for it to expire or finish before creating another checkout.");
            prior.markFailedFromProvider(null);
        }

        PaymentType type = PaymentType.FULL_PAYMENT;
        String reference = "HOTEL-" + reservationId + "-" +
                UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        reservation.touchForPayment();
        reservations.saveAndFlush(reservation);
        Payment payment = payments.save(new Payment(reservationId, customer.getUsername(), type,
                due, "XENDIT", reference, PaymentStatus.PENDING));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reference_id", reference);
        body.put("session_type", "PAY");
        body.put("mode", "PAYMENT_LINK");
        body.put("amount", due.setScale(2, RoundingMode.HALF_UP));
        body.put("currency", "PHP");
        body.put("country", "PH");
        body.put("capture_method", "AUTOMATIC");
        body.put("description", "Hotel reservation #" + reservationId + " - full payment");
        body.put("success_return_url", publicBaseUrl + "/customer/payments?checkout=returned");
        body.put("cancel_return_url", publicBaseUrl + "/customer/reservations");
        body.put("metadata", Map.of("reservation_id", reservationId.toString(),
                "hotel_payment_id", payment.getId().toString()));

        Map<?, ?> response;
        try {
            response = client.post().uri("/sessions")
                    .headers(headers -> headers.setBasicAuth(secretKey, ""))
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException ex) {
            // Preserve Xendit's safe error details so a rejected API request can be diagnosed
            // from the application message without exposing the secret API key.
            String providerDetail = "HTTP " + ex.getStatusCode().value();
            try {
                JsonNode error = mapper.readTree(ex.getResponseBodyAsString());
                String errorCode = error.path("error_code").asText("");
                String errorMessage = error.path("message").asText("");
                if (!errorCode.isBlank()) providerDetail += " [" + errorCode + "]";
                if (!errorMessage.isBlank()) {
                    String safeMessage = errorMessage.replaceAll("[\\r\\n\\t]", " ").trim();
                    if (safeMessage.length() > 240) safeMessage = safeMessage.substring(0, 240);
                    providerDetail += ": " + safeMessage;
                }
            } catch (Exception ignored) {
                // The HTTP status remains useful even when Xendit returns a non-JSON error body.
            }
            throw new IllegalStateException(
                    "Xendit rejected the checkout request (" + providerDetail
                            + "). Check that the Test Mode API key and enabled payment channels are correct.",
                    ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException(
                    "Xendit checkout could not be reached within the allowed time. No payment was confirmed. Please try again.",
                    ex);
        }

        if (response == null || !(response.get("payment_session_id") instanceof String sessionId)
                || !(response.get("payment_link_url") instanceof String checkoutUrl)
                || !checkoutUrl.startsWith("https://")) {
            throw new IllegalStateException("Xendit returned an incomplete hosted checkout response.");
        }
        payment.attachCheckoutSession(sessionId, checkoutUrl);
        audit.record(username, "XENDIT_CHECKOUT_CREATED", "PAYMENT", payment.getId().toString(),
                "Xendit Test Mode hosted checkout created for reservation #" + reservationId
                        + "; reference " + reference);
        return checkoutUrl;
    }


    /**
     * Creates a separate hosted checkout for the full additional charge of an
     * Admin-approved reservation extension. It never changes the stay dates;
     * the dates change only after the signed provider webhook confirms payment.
     */
    public String createExtensionCheckout(Long extensionId, String username) {
        requireTestConfiguration();
        ReservationExtensionRequest extension = extensions.approvedRequestForPayment(extensionId, username);
        Payment payment;
        Long previousPaymentId = extension.getPaymentId();
        if (previousPaymentId != null) {
            payment = payments.findById(previousPaymentId)
                    .orElseThrow(() -> new IllegalStateException("The linked extension payment was not found."));
            if (payment.getStatus() == PaymentStatus.PAID)
                throw new IllegalStateException("This extension has already been paid.");
            if (payment.getCheckoutUrl() != null
                    && payment.getProviderCheckoutSessionId() != null
                    && payment.getStatus() == PaymentStatus.PENDING)
                return payment.getCheckoutUrl();
            if (payment.getStatus() != PaymentStatus.FAILED && payment.getStatus() != PaymentStatus.VOIDED)
                throw new IllegalStateException("The previous extension checkout is still pending. Wait for Xendit to confirm its status.");
            payment = createExtensionPayment(extension, extensionId, username);
            extensions.replaceFailedPayment(extensionId, previousPaymentId, payment.getId(), username);
        } else {
            payment = createExtensionPayment(extension, extensionId, username);
            extensions.attachPayment(extensionId, payment.getId(), username);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reference_id", payment.getTransactionReference());
        body.put("session_type", "PAY");
        body.put("mode", "PAYMENT_LINK");
        body.put("amount", extension.getExtensionAmount().setScale(2, RoundingMode.HALF_UP));
        body.put("currency", "PHP");
        body.put("country", "PH");
        body.put("capture_method", "AUTOMATIC");
        body.put("description", "Hotel reservation #" + extension.getReservationId()
                + " - extension #" + extensionId + " full additional charge");
        body.put("success_return_url", publicBaseUrl + "/customer/reservation-extensions?payment=returned");
        body.put("cancel_return_url", publicBaseUrl + "/customer/reservation-extensions");
        body.put("metadata", Map.of("reservation_id", extension.getReservationId().toString(),
                "extension_request_id", extensionId.toString(), "hotel_payment_id", payment.getId().toString()));

        Map<?, ?> response;
        try {
            response = client.post().uri("/sessions")
                    .headers(headers -> headers.setBasicAuth(secretKey, ""))
                    .header("Content-Type", "application/json")
                    .body(body).retrieve().body(Map.class);
        } catch (RestClientException ex) {
            throw new IllegalStateException(
                    "Xendit could not create the extension checkout. No extension payment was confirmed. Please retry.",
                    ex);
        }
        if (response == null || !(response.get("payment_session_id") instanceof String sessionId)
                || !(response.get("payment_link_url") instanceof String checkoutUrl)
                || !checkoutUrl.startsWith("https://"))
            throw new IllegalStateException("Xendit returned an incomplete extension checkout response.");
        payment.attachCheckoutSession(sessionId, checkoutUrl);
        audit.record(username, "XENDIT_EXTENSION_CHECKOUT_CREATED", "PAYMENT",
                payment.getId().toString(), "Hosted checkout created for extension #" + extensionId
                        + "; full additional charge PHP " + extension.getExtensionAmount() + ".");
        return checkoutUrl;
    }

    private Payment createExtensionPayment(ReservationExtensionRequest extension, Long extensionId,
                                            String username) {
        String reference = "EXT-" + extensionId + "-"
                + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        Payment payment = payments.save(new Payment(extension.getReservationId(), username,
                PaymentType.EXTENSION_PAYMENT, extension.getExtensionAmount(),
                "XENDIT", reference, PaymentStatus.PENDING));
        payment.linkExtensionRequest(extensionId);
        return payment;
    }

    public void handleWebhook(byte[] rawBody, String callbackToken) {
        requireTestConfiguration();
        if (callbackToken == null || !MessageDigest.isEqual(
                webhookToken.getBytes(StandardCharsets.UTF_8),
                callbackToken.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Invalid Xendit webhook token.");
        }

        JsonNode root;
        try {
            root = mapper.readTree(rawBody);
        } catch (Exception ex) {
            throw new IllegalArgumentException("The Xendit webhook payload is not valid JSON.", ex);
        }

        String event = root.path("event").asText("");
        JsonNode data = root.path("data");
        if ("payment_session.completed".equals(event)) {
            processCompletedSession(data, event);
        } else if ("payment_session.expired".equals(event)) {
            processExpiredSession(data, event);
        } else if ("refund.succeeded".equals(event) || "refund.failed".equals(event)) {
            processRefundEvent(data, event);
        }
    }

    public void reconcilePendingCheckouts() {
        if (!isConfigured()) return;
        List<Payment> pending = payments.findByPaymentMethodAndStatusAndProviderCheckoutSessionIdIsNotNull(
                "XENDIT", PaymentStatus.PENDING);
        for (Payment payment : pending) {
            try {
                Map<?, ?> response = client.get()
                        .uri("/sessions/{id}", payment.getProviderCheckoutSessionId())
                        .headers(headers -> headers.setBasicAuth(secretKey, ""))
                        .retrieve().body(Map.class);
                if (response == null) continue;
                JsonNode session = mapper.valueToTree(response);
                if (!payment.getTransactionReference().equals(session.path("reference_id").asText(""))
                        || !payment.getProviderCheckoutSessionId().equals(
                                session.path("payment_session_id").asText(""))) continue;
                String status = session.path("status").asText("");
                if ("COMPLETED".equals(status)) {
                    processCompletedSession(session, "payment_session.completed");
                } else if ("EXPIRED".equals(status)) {
                    processExpiredSession(session, "payment_session.expired");
                }
            } catch (RuntimeException ex) {
                // Leave the payment pending; the next scheduled reconciliation or provider webhook can resolve it.
            }
        }
    }

    private void processCompletedSession(JsonNode data, String event) {
        String sessionId = data.path("payment_session_id").asText("");
        String reference = data.path("reference_id").asText("");
        String paymentId = data.path("payment_id").asText("");
        String eventKey = event + ":" + sessionId;
        if (sessionId.isBlank() || reference.isBlank() || paymentId.isBlank()
                || !"COMPLETED".equals(data.path("status").asText(""))
                || !"PAY".equals(data.path("session_type").asText(""))
                || !"PHP".equals(data.path("currency").asText(""))
                || !"PH".equals(data.path("country").asText(""))) {
            throw new IllegalArgumentException("The Xendit completed-session webhook is incomplete or invalid.");
        }
        if (processedEvents.existsByEventKey(eventKey)) return;

        Payment payment = payments.findByTransactionReference(reference)
                .orElseThrow(() -> new IllegalArgumentException("No matching hotel payment was found."));
        BigDecimal providerAmount = data.path("amount").decimalValue();
        if (providerAmount.compareTo(payment.getAmount()) != 0)
            throw new IllegalArgumentException("The Xendit payment amount does not match the hotel payment.");
        if (!Objects.equals(payment.getProviderCheckoutSessionId(), sessionId))
            throw new IllegalArgumentException("The Xendit session does not match the pending hotel payment.");

        if (payment.getStatus() != PaymentStatus.PAID) {
            payment.markPaidFromProvider(sessionId, paymentId);
            audit.record("XENDIT_WEBHOOK", "PAYMENT_CONFIRMED", "PAYMENT", payment.getId().toString(),
                    "Xendit Test Mode confirmed " + payment.getPaymentType()
                            + "; provider payment ID " + paymentId);
            if (payment.getPaymentType() == PaymentType.EXTENSION_PAYMENT) {
                if (payment.getExtensionRequestId() == null)
                    throw new IllegalStateException("The extension payment is missing its extension request link.");
                extensions.completePayment(payment.getExtensionRequestId(), payment.getId(), payment.getAmount());
                sendPaymentReceipt(payment);
                processedEvents.save(new ProcessedXenditEvent(eventKey, event));
                return;
            }
            submitPaidHoldToManagerReview(payment);
            sendPaymentReceipt(payment);
            Reservation linkedReservation = reservations.findById(payment.getReservationId()).orElse(null);
            if (linkedReservation == null
                    || (linkedReservation.getStatus() != ReservationStatus.PENDING
                        && linkedReservation.getStatus() != ReservationStatus.APPROVED
                        && linkedReservation.getStatus() != ReservationStatus.CHECKED_IN)) {
                notifications.notifyAdminsInAppOnly("Payment received for inactive reservation",
                        "Xendit confirmed payment #" + payment.getId()
                                + " for a reservation that is no longer active. Review and refund if required.",
                        "PAYMENT", "XENDIT-INACTIVE-RESERVATION-" + payment.getId());
            } else {
                BigDecimal paidIncludingThis = paidAmountForReservation(payment.getReservationId());
                BigDecimal paidBefore = paidIncludingThis.subtract(payment.getAmount()).max(BigDecimal.ZERO);
                BigDecimal expectedAmount = linkedReservation.getTotalAmount().subtract(paidBefore)
                        .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                if (expectedAmount.compareTo(payment.getAmount()) != 0) {
                    audit.record("XENDIT_WEBHOOK", "PAYMENT_AMOUNT_STALE", "PAYMENT",
                            payment.getId().toString(),
                            "The provider payment succeeded but the reservation amount changed after checkout creation.");
                    notifications.notifyAdminsInAppOnly("Payment needs reservation reconciliation",
                            "Xendit confirmed payment #" + payment.getId()
                                    + " but the reservation total changed after checkout creation. Review the balance or refund.",
                            "PAYMENT", "XENDIT-STALE-AMOUNT-" + payment.getId());
                }
            }
        }
        processedEvents.save(new ProcessedXenditEvent(eventKey, event));
    }

    private void processExpiredSession(JsonNode data, String event) {
        String sessionId = data.path("payment_session_id").asText("");
        String reference = data.path("reference_id").asText("");
        String eventKey = event + ":" + sessionId;
        if (sessionId.isBlank() || reference.isBlank()
                || !"EXPIRED".equals(data.path("status").asText("")))
            throw new IllegalArgumentException("The Xendit expired-session webhook is incomplete or invalid.");
        if (processedEvents.existsByEventKey(eventKey)) return;

        Payment payment = payments.findByTransactionReference(reference)
                .orElseThrow(() -> new IllegalArgumentException("No matching hotel payment was found."));
        if (!Objects.equals(payment.getProviderCheckoutSessionId(), sessionId))
            throw new IllegalArgumentException("The expired Xendit session does not match the hotel payment.");
        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.markFailedFromProvider(null);
            Reservation reservation = reservations.findById(payment.getReservationId()).orElse(null);
            if (reservation != null && reservation.getStatus() == ReservationStatus.PAYMENT_PENDING) {
                reservation.changeStatus(ReservationStatus.EXPIRED);
                audit.record("XENDIT_WEBHOOK", "UNPAID_RESERVATION_HOLD_EXPIRED", "RESERVATION",
                        reservation.getId().toString(), "Unpaid reservation hold released after Xendit checkout expired.");
            }
            notifications.notifyUserInAppOnly(payment.getCustomerUsername(), "Payment session expired",
                    "Your checkout for reservation #" + payment.getReservationId()
                            + " expired without payment. You can start a new reservation if the room is still available.",
                    "PAYMENT");
        }
        processedEvents.save(new ProcessedXenditEvent(eventKey, event));
    }

    private void processRefundEvent(JsonNode data, String event) {
        String refundId = data.path("refund_id").asText("");
        String providerPaymentId = data.path("payment_id").asText("");
        String status = data.path("status").asText("");
        String eventKey = event + ":" + refundId;
        if (refundId.isBlank() || providerPaymentId.isBlank()
                || !"PHP".equals(data.path("currency").asText(""))
                || (!"SUCCEEDED".equals(status) && !"FAILED".equals(status)))
            throw new IllegalArgumentException("The Xendit refund webhook is incomplete or invalid.");
        if (processedEvents.existsByEventKey(eventKey)) return;

        Payment payment = payments.findByProviderPaymentId(providerPaymentId)
                .orElseThrow(() -> new IllegalArgumentException("No matching hotel payment was found for this refund."));
        if (!"PHP".equals(data.path("currency").asText(""))
                || data.path("amount").decimalValue().compareTo(payment.getAmount()) != 0)
            throw new IllegalArgumentException("The Xendit refund amount or currency does not match the original payment.");
        if (payment.getProviderRefundId() != null
                && !payment.getProviderRefundId().equals(refundId))
            throw new IllegalArgumentException("The refund ID does not match the pending hotel refund.");

        if ("SUCCEEDED".equals(status) && payment.getStatus() == PaymentStatus.REFUND_PENDING) {
            payment.markRefunded(Instant.now(clock), refundId);
            audit.record("XENDIT_WEBHOOK", "REFUND_CONFIRMED", "PAYMENT", payment.getId().toString(),
                    "Xendit confirmed the refund.");
            notifications.notifyUserOnce(payment.getCustomerUsername(), "Hotel refund receipt",
                    hotelName + "\nYour refund has been confirmed by Xendit.\n\nPayment reference: "
                            + payment.getTransactionReference() + "\nRefund reference: " + refundId
                            + "\nRefund amount: PHP " + payment.getAmount() + "\nRefund confirmed: "
                            + Instant.now(clock),
                    "PAYMENT", "XENDIT-REFUND-RECEIPT-" + payment.getId());
        } else if ("FAILED".equals(status) && payment.getStatus() == PaymentStatus.REFUND_PENDING) {
            payment.markRefundFailed(refundId);
            notifications.notifyUser(payment.getCustomerUsername(), "Refund processing failed",
                    "Xendit could not complete the refund for payment " + payment.getTransactionReference()
                            + ". The hotel administrators have been notified for review.", "PAYMENT");
            notifications.notifyAdmins("Xendit refund failed",
                    "Refund " + refundId + " failed for payment #" + payment.getId() + ". Review it in Xendit.",
                    "PAYMENT");
        }
        processedEvents.save(new ProcessedXenditEvent(eventKey, event));
    }

    private void submitPaidHoldToManagerReview(Payment payment) {
        Reservation reservation = reservations.findById(payment.getReservationId()).orElse(null);
        if (reservation == null || reservation.getStatus() != ReservationStatus.PAYMENT_PENDING) return;
        reservation.changeStatus(ReservationStatus.PENDING);
        audit.record("XENDIT_WEBHOOK", "RESERVATION_SUBMITTED_AFTER_FULL_PAYMENT", "RESERVATION",
                reservation.getId().toString(), "Full payment verified. Reservation is now pending manager review.");
        notifications.notifyAdminsOnce("New paid reservation request",
                "Reservation #" + reservation.getId() + " from " + reservation.getCustomerUsername()
                        + " has been fully paid and is awaiting manager review.",
                "RESERVATION", "XENDIT-PAID-RESERVATION-" + reservation.getId());
    }

    private void sendPaymentReceipt(Payment payment) {
        Reservation reservation = reservations.findById(payment.getReservationId()).orElse(null);
        StringBuilder receipt = new StringBuilder().append(hotelName)
                .append("\nYour payment has been confirmed. This email is your hotel payment receipt.\n\n")
                .append("Receipt number: PAY-").append(payment.getId()).append("\n")
                .append("Payment reference: ").append(payment.getTransactionReference()).append("\n")
                .append("Payment method: XENDIT").append("\n")
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
                "PAYMENT", "XENDIT-PAYMENT-RECEIPT-" + payment.getId());
    }

    public void refund(Payment payment, PaymentRefundRequest request) {
        requireTestConfiguration();
        if (payment == null || request == null || !"XENDIT".equalsIgnoreCase(payment.getPaymentMethod())
                || payment.getProviderPaymentId() == null || payment.getProviderPaymentId().isBlank())
            throw new IllegalStateException("This payment has no confirmed Xendit payment reference to refund.");

        String reason = request.getReason() == null ? "" : request.getReason().toLowerCase(Locale.ROOT);
        String providerReason = reason.contains("fraud") ? "FRAUDULENT"
                : reason.contains("duplicate") ? "DUPLICATE"
                : reason.contains("cancel") ? "CANCELLATION"
                : reason.contains("customer") ? "REQUESTED_BY_CUSTOMER" : "OTHERS";
        String refundReference = "HOTEL-REFUND-" + request.getId();
        Map<String, Object> body = Map.of("reference_id", refundReference,
                "payment_id", payment.getProviderPaymentId(), "reason", providerReason);

        Map<?, ?> response = client.post().uri("/v3/refunds")
                .headers(headers -> {
                    headers.setBasicAuth(secretKey, "");
                    headers.set("api-version", "2024-11-11");
                    headers.set("Idempotency-Key", "hotel-refund-request-" + request.getId());
                })
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (requestSpec, responseSpec) -> {
                    throw new IllegalStateException("Xendit could not start the refund. Check the provider dashboard before retrying.");
                })
                .body(Map.class);

        if (response == null) throw new IllegalStateException("Xendit returned an empty refund response.");
        String refundId = Objects.toString(response.get("id"), Objects.toString(response.get("refund_id"), ""));
        String status = Objects.toString(response.get("status"), "PENDING").toUpperCase(Locale.ROOT);
        if (refundId.isBlank()) throw new IllegalStateException("Xendit did not return a refund reference.");
        payment.markRefundPending(refundId, status);
        if ("SUCCEEDED".equals(status)) {
            payment.markRefunded(Instant.now(clock), refundId);
            notifications.notifyUserOnce(payment.getCustomerUsername(), "Hotel refund receipt",
                    hotelName + "\nYour refund has been confirmed by Xendit.\n\nPayment reference: "
                            + payment.getTransactionReference() + "\nRefund reference: " + refundId
                            + "\nRefund amount: PHP " + payment.getAmount(),
                    "PAYMENT", "XENDIT-REFUND-RECEIPT-" + payment.getId());
        } else if ("FAILED".equals(status)) {
            payment.markRefundFailed(refundId);
            notifications.notifyAdmins("Xendit refund failed",
                    "Xendit rejected the refund for payment #" + payment.getId()
                            + ". Review the provider dashboard.", "PAYMENT");
        }
    }

    private BigDecimal paidAmountForReservation(Long reservationId) {
        BigDecimal paid = payments.sumPaidForReservation(reservationId, PaymentStatus.PAID);
        BigDecimal pendingRefund = payments.sumPaidForReservation(reservationId, PaymentStatus.REFUND_PENDING);
        return (paid == null ? BigDecimal.ZERO : paid)
                .add(pendingRefund == null ? BigDecimal.ZERO : pendingRefund);
    }

    private void requireTestConfiguration() {
        if (!isConfigured())
            throw new IllegalStateException("Xendit Test Mode is not fully configured. No payment was taken.");
        if (!publicBaseUrl.startsWith("https://"))
            throw new IllegalStateException("Set HOTEL_PUBLIC_BASE_URL to the public HTTPS Codespaces URL before starting Xendit checkout.");
    }
}
