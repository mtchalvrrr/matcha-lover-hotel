package com.hotel.system.support;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SupportCaseRepository extends JpaRepository<SupportCase, Long> {
    List<SupportCase> findByCustomerUsernameAndVisibleTrueOrderByUpdatedAtDesc(String customerUsername);
    List<SupportCase> findByVisibleTrueOrderByUpdatedAtDesc();
    Optional<SupportCase> findByIdAndCustomerUsernameAndVisibleTrue(Long id, String customerUsername);
    Optional<SupportCase> findByIdAndVisibleTrue(Long id);
}
