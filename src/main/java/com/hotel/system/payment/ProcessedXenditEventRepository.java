package com.hotel.system.payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedXenditEventRepository extends JpaRepository<ProcessedXenditEvent, Long> {
    boolean existsByEventKey(String eventKey);
}
