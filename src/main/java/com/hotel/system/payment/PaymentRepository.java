package com.hotel.system.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findAllByOrderByCreatedAtDesc();
    List<Payment> findByCustomerUsernameOrderByCreatedAtDesc(String username);
    List<Payment> findByReservationIdOrderByCreatedAtAsc(Long reservationId);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.reservationId = :reservationId and p.status = com.hotel.system.payment.PaymentStatus.PAID")
    BigDecimal sumPaidForReservation(@Param("reservationId") Long reservationId);
}