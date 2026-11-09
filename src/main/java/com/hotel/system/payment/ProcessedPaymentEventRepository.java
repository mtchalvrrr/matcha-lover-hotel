package com.hotel.system.payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedPaymentEventRepository extends JpaRepository<ProcessedPaymentEvent, Long> {
    boolean existsByEventKey(String eventKey);
}
