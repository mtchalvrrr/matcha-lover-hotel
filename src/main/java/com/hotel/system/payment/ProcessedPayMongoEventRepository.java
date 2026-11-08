package com.hotel.system.payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedPayMongoEventRepository extends JpaRepository<ProcessedPayMongoEvent, Long> {
    boolean existsByEventId(String eventId);
}
