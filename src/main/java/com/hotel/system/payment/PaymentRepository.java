package com.hotel.system.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;
import java.time.Instant;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findAllByOrderByCreatedAtDesc();
    List<Payment> findByCreatedAtBetweenAndStatusOrderByCreatedAtAsc(Instant from, Instant to, PaymentStatus status);
    List<Payment> findByRefundedAtBetweenOrderByRefundedAtAsc(Instant from, Instant to);
    List<Payment> findByCustomerUsernameOrderByCreatedAtDesc(String username);
    List<Payment> findByReservationIdOrderByCreatedAtAsc(Long reservationId);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.reservationId = :reservationId and p.status = :status")
    BigDecimal sumPaidForReservation(@Param("reservationId") Long reservationId, @Param("status") PaymentStatus status);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") PaymentStatus status);
}
