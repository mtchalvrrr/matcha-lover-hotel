package com.hotel.system.payment;

import com.hotel.system.account.SystemUser;
import com.hotel.system.account.SystemUserRepository;
import com.hotel.system.audit.AuditService;
import com.hotel.system.reservation.Reservation;
import com.hotel.system.reservation.ReservationRepository;
import com.hotel.system.reservation.ReservationStatus;
import com.hotel.system.reservation.ReservationSource;
import com.hotel.system.notification.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PaymentService {
    private final PaymentRepository payments;
    private final PaymentRefundRequestRepository refundRequests;
    private final ReservationRepository reservations;
    private final SystemUserRepository users;
    private final AuditService audit;
    private final String paymentMode;
    private final NotificationService notifications;
    private final Clock clock;
    private final PayMongoService payMongo;

    public PaymentService(PaymentRepository payments, PaymentRefundRequestRepository refundRequests,
                          ReservationRepository reservations, SystemUserRepository users, AuditService audit,
                          @Value("${payment.mode:SIMULATED}") String paymentMode,
                          NotificationService notifications, Clock clock) {
        this(payments, refundRequests, reservations, users, audit, paymentMode, notifications, clock, null);
    }

    @Autowired
    public PaymentService(PaymentRepository payments, PaymentRefundRequestRepository refundRequests,
                          ReservationRepository reservations, SystemUserRepository users, AuditService audit,
                          @Value("${payment.mode:SIMULATED}") String paymentMode,
                          NotificationService notifications, Clock clock, PayMongoService payMongo) {
        this.payments = payments;
        this.refundRequests = refundRequests;
        this.reservations = reservations;
        this.users = users;
        this.audit = audit;
        this.paymentMode = paymentMode;
        this.notifications = notifications;
        this.clock = clock;
        this.payMongo = payMongo;
    }

    public Payment payNextDue(Long reservationId, String username) {
        SystemUser customer = users.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new IllegalArgumentException("Customer account was not found."));
        Reservation reservation = reservations.findById(reservationId)
            .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (!java.util.Objects.equals(reservation.getCustomerId(), customer.getId())) {
            throw new IllegalArgumentException("Reservation not found.");
        }
        if (reservation.getStatus() != ReservationStatus.APPROVED
                && reservation.getStatus() != ReservationStatus.CHECKED_IN) {
            throw new IllegalStateException("Payments are available only for approved or currently checked-in reservations.");
        }
        if (!"SIMULATED".equalsIgnoreCase(paymentMode)) {
            throw new IllegalStateException("A verified payment gateway is not configured. No payment was taken.");
        }

        BigDecimal paid = payments.sumPaidForReservation(reservationId, PaymentStatus.PAID);
        BigDecimal due = reservation.getTotalAmount().subtract(paid).setScale(2, RoundingMode.HALF_UP);
        if (due.signum() <= 0) throw new IllegalStateException("This reservation is already fully paid.");

        PaymentType type;
        BigDecimal amount;
        if (paid.signum() == 0) {
            type = PaymentType.DOWN_PAYMENT;
            amount = reservation.getTotalAmount().multiply(new BigDecimal("0.50"))
                .setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(due) > 0) amount = due;
        } else {
            type = PaymentType.BALANCE;
            amount = due;
        }

        reservation.touchForPayment();
        reservations.save(reservation);
        String reference = "SIM-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        Payment payment = payments.save(new Payment(reservationId, customer.getUsername(),
            type, amount, "SIMULATED", reference, PaymentStatus.PAID));
        audit.record(username, "SIMULATED_PAYMENT_RECORDED", "RESERVATION", reservationId.toString(),
            type + " payment of " + amount + " recorded using simulated mode; reference " + reference);
        notifications.notifyUser(username, "Payment recorded", "A simulated " + type
            + " payment of ₱" + amount + " was recorded for reservation #" + reservationId + ".", "PAYMENT");
        return payment;
    }


    public Payment recordWalkInPayment(Long reservationId, String method, String adminUsername) {
        Reservation reservation = reservations.findById(reservationId)
            .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (reservation.getSource() != ReservationSource.WALK_IN)
            throw new IllegalArgumentException("This is not a walk-in reservation.");
        if (reservation.getStatus() != ReservationStatus.APPROVED)
            throw new IllegalStateException("Only approved walk-in reservations can receive onsite payment.");
        String paymentMethod = method == null ? "" : method.trim().toUpperCase();
        if (!List.of("CASH", "CARD", "GCASH", "BANK_TRANSFER").contains(paymentMethod))
            throw new IllegalArgumentException("Choose a valid onsite payment method.");
        if (payments.sumPaidForReservation(reservationId, PaymentStatus.PAID).signum() > 0)
            throw new IllegalStateException("Payment has already been recorded for this walk-in reservation.");
        reservation.touchForPayment();
        reservations.save(reservation);
        String reference = "WALKIN-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        Payment payment = payments.save(new Payment(reservationId, reservation.getCustomerUsername(),
            PaymentType.WALK_IN_FULL, reservation.getTotalAmount(), paymentMethod, reference, PaymentStatus.PAID));
        audit.record(adminUsername, "WALK_IN_PAYMENT_RECORDED", "RESERVATION", reservationId.toString(),
            "Recorded onsite " + paymentMethod + " payment of " + reservation.getTotalAmount() + "; reference " + reference);
        notifications.notifyAdmins("Walk-in payment recorded", "Admin " + adminUsername
            + " recorded onsite payment for reservation #" + reservationId + ".", "PAYMENT");
        return payment;
    }


    public PaymentRefundRequest requestRefund(Long paymentId, String adminUsername, String reason) {
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment record not found."));
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isBlank() || cleanReason.length() > 500) {
            throw new IllegalArgumentException("Enter a refund reason up to 500 characters.");
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new IllegalStateException("Only paid transactions can be submitted for refund review.");
        }
        if (refundRequests.existsByPaymentIdAndStatus(paymentId, RefundRequestStatus.PENDING)) {
            throw new IllegalStateException("A refund request is already awaiting authorization.");
        }
        PaymentRefundRequest request = refundRequests.save(
                new PaymentRefundRequest(paymentId, adminUsername, cleanReason));
        audit.record(adminUsername, "PAYMENT_REFUND_REQUESTED", "PAYMENT", paymentId.toString(),
                "Refund request #" + request.getId() + " submitted. Reason: " + cleanReason);
        notifications.notifyAdmins("Payment refund awaiting authorization",
                "Admin " + adminUsername + " requested a refund for payment #" + paymentId
                        + ". A different administrator must review it.", "PAYMENT");
        return request;
    }

    public void reviewRefund(Long requestId, boolean approve, String reviewer, String reviewReason) {
        PaymentRefundRequest request = refundRequests.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Refund request not found."));
        if (request.getStatus() != RefundRequestStatus.PENDING) {
            throw new IllegalStateException("This refund request has already been reviewed.");
        }
        if (reviewer == null || reviewer.isBlank()
                || reviewer.equalsIgnoreCase(request.getRequestedBy())) {
            throw new IllegalArgumentException("A different administrator must authorize this refund.");
        }
        Payment payment = payments.findById(request.getPaymentId())
                .orElseThrow(() -> new IllegalArgumentException("Payment record not found."));
        if (approve) {
            if (payment.getStatus() != PaymentStatus.PAID) {
                throw new IllegalStateException("The payment is no longer eligible for refund.");
            }
            touchReservation(payment.getReservationId());
            if ("PAYMONGO".equalsIgnoreCase(payment.getPaymentMethod())) {
                if (payMongo == null) throw new IllegalStateException("PayMongo refund processing is unavailable.");
                payMongo.refund(payment, request.getReason());
            } else {
                payment.markRefunded(Instant.now(clock));
            }
        }
        request.review(approve, reviewer, reviewReason, Instant.now(clock));
        audit.record(reviewer, approve ? "PAYMENT_REFUND_AUTHORIZED" : "PAYMENT_REFUND_REJECTED",
                "PAYMENT", payment.getId().toString(),
                "Refund request #" + requestId + " submitted by " + request.getRequestedBy()
                        + "; reason: " + request.getReason()
                        + (approve ? "; refund status recorded as REFUNDED (settlement must be completed through the original payment channel)."
                                   : "; review reason: " + (reviewReason == null ? "" : reviewReason.trim())));
        String message = approve
                ? (payment.getStatus() == PaymentStatus.REFUND_PENDING
                    ? "Your refund for payment " + payment.getTransactionReference()
                        + " was accepted by the hotel and is being processed by PayMongo. We will notify you when the provider confirms the result."
                    : "Your refund for payment " + payment.getTransactionReference()
                        + " has been confirmed by the payment provider.")
                : "Your refund request for payment " + payment.getTransactionReference()
                    + " was rejected. Reason: " + (reviewReason == null ? "" : reviewReason.trim());
        if (payment.getCustomerUsername().startsWith("WALK-IN (")) {
            notifications.notifyAdmins("Walk-in refund request reviewed", message, "PAYMENT");
        } else {
            notifications.notifyUser(payment.getCustomerUsername(),
                    approve ? "Refund approved" : "Refund request rejected", message, "PAYMENT");
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentRefundRequest> allRefundRequests() {
        return refundRequests.findAllByOrderByCreatedAtDesc();
    }

    public void voidPendingPayment(Long paymentId, String adminUsername, String reason) {
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment record not found."));
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isBlank() || cleanReason.length() > 500) {
            throw new IllegalArgumentException("Enter a void reason up to 500 characters.");
        }
        touchReservation(payment.getReservationId());
        payment.markVoided();
        audit.record(adminUsername, "PAYMENT_VOIDED", "PAYMENT", paymentId.toString(),
                "Voided pending payment for reservation #" + payment.getReservationId()
                        + ". Reason: " + cleanReason);
    }


    private void touchReservation(Long reservationId) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new IllegalStateException("The linked reservation no longer exists."));
        reservation.touchForPayment();
        reservations.save(reservation);
    }

    @Transactional(readOnly = true)
    public Payment customerPayment(Long paymentId, String username) {
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment record not found."));
        if (!payment.getCustomerUsername().equalsIgnoreCase(username)) {
            throw new IllegalArgumentException("Payment record not found.");
        }
        return payment;
    }

    @Transactional(readOnly = true)
    public Payment paymentDetails(Long paymentId) {
        return payments.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment record not found."));
    }

    @Transactional(readOnly = true)
    public List<Payment> allPayments() { return payments.findAllByOrderByCreatedAtDesc(); }

    @Transactional(readOnly = true)
    public List<Payment> customerPayments(String username) {
        return payments.findByCustomerUsernameOrderByCreatedAtDesc(username);
    }

    @Transactional(readOnly = true)
    public BigDecimal amountPaid(Long reservationId) {
        return payments.sumPaidForReservation(reservationId, PaymentStatus.PAID);
    }
}