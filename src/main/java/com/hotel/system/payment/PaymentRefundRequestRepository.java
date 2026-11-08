package com.hotel.system.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaymentRefundRequestRepository extends JpaRepository<PaymentRefundRequest, Long> {
    boolean existsByPaymentIdAndStatus(Long paymentId, RefundRequestStatus status);
    List<PaymentRefundRequest> findAllByOrderByCreatedAtDesc();
}
