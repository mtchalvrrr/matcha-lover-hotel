package com.hotel.system.payment;

import com.hotel.system.account.SystemUser;
import com.hotel.system.account.SystemUserRepository;
import com.hotel.system.audit.AuditService;
import com.hotel.system.reservation.Reservation;
import com.hotel.system.reservation.ReservationRepository;
import com.hotel.system.reservation.ReservationStatus;
import com.hotel.system.reservation.ReservationSource;
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

    public PaymentService(PaymentRepository payments, ReservationRepository reservations,
                          SystemUserRepository users, AuditService audit,
                          @Value("${payment.mode:SIMULATED}") String paymentMode) {
        this.payments = payments;
        this.reservations = reservations;
        this.users = users;
        this.audit = audit;
        this.paymentMode = paymentMode;
    }

    public Payment payNextDue(Long reservationId, String username) {
        SystemUser customer = users.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new IllegalArgumentException("Customer account was not found."));
        Reservation reservation = reservations.findById(reservationId)
            .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (!java.util.Objects.equals(reservation.getCustomerId(), customer.getId())) {
            throw new IllegalArgumentException("Reservation not found.");
        }
        if (reservation.getStatus() != ReservationStatus.APPROVED) {
            throw new IllegalStateException("Only approved reservations can be paid.");
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
        return payment;
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