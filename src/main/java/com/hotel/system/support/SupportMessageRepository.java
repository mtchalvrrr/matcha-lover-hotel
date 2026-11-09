package com.hotel.system.support;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    List<SupportMessage> findBySupportCaseIdOrderByCreatedAtAsc(Long supportCaseId);
}
