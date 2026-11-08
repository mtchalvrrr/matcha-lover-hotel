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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PaymentService {
    private final PaymentRepository payments;
    private final ReservationRepository reservations;
    private final SystemUserRepository users;
    private final AuditService audit;
    private final String paymentMode;
    private final NotificationService notifications;

    public PaymentService(PaymentRepository payments, ReservationRepository reservations,
                          SystemUserRepository users, AuditService audit,
                          @Value("${payment.mode:SIMULATED}") String paymentMode, NotificationService notifications) {
        this.payments = payments;
        this.reservations = reservations;
        this.users = users;
        this.audit = audit;
        this.paymentMode = paymentMode;
        this.notifications = notifications;
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
        String reference = "WALKIN-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        Payment payment = payments.save(new Payment(reservationId, reservation.getCustomerUsername(),
            PaymentType.WALK_IN_FULL, reservation.getTotalAmount(), paymentMethod, reference, PaymentStatus.PAID));
        audit.record(adminUsername, "WALK_IN_PAYMENT_RECORDED", "RESERVATION", reservationId.toString(),
            "Recorded onsite " + paymentMethod + " payment of " + reservation.getTotalAmount() + "; reference " + reference);
        notifications.notifyAdmins("Walk-in payment recorded", "Admin " + adminUsername
            + " recorded onsite payment for reservation #" + reservationId + ".", "PAYMENT");
        return payment;
    }


    public void refundPayment(Long paymentId, String adminUsername, String reason) {
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment record not found."));
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isBlank() || cleanReason.length() > 500) {
            throw new IllegalArgumentException("Enter a refund reason up to 500 characters.");
        }
        payment.markRefunded();
        audit.record(adminUsername, "PAYMENT_REFUNDED", "PAYMENT", paymentId.toString(),
                "Refunded " + payment.getAmount() + " for reservation #" + payment.getReservationId()
                        + ". Reason: " + cleanReason);
        String refundNotice = "Payment " + payment.getTransactionReference()
                + " was marked as refunded. Contact the hotel for settlement details.";
        if (payment.getCustomerUsername().startsWith("WALK-IN (")) {
            notifications.notifyAdmins("Walk-in payment refunded", refundNotice, "PAYMENT");
        } else {
            notifications.notifyUser(payment.getCustomerUsername(), "Payment refunded", refundNotice, "PAYMENT");
        }
    }

    public void voidPendingPayment(Long paymentId, String adminUsername, String reason) {
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment record not found."));
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isBlank() || cleanReason.length() > 500) {
            throw new IllegalArgumentException("Enter a void reason up to 500 characters.");
        }
        payment.markVoided();
        audit.record(adminUsername, "PAYMENT_VOIDED", "PAYMENT", paymentId.toString(),
                "Voided pending payment for reservation #" + payment.getReservationId()
                        + ". Reason: " + cleanReason);
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