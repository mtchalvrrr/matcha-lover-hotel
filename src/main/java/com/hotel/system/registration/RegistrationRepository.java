package com.hotel.system.registration;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RegistrationRepository extends JpaRepository<RegistrationApplication, Long> {
    List<RegistrationApplication> findByStatusOrderBySubmittedAtAsc(RegistrationStatus status);
    boolean existsByEmailIgnoreCaseAndStatusIn(String email, List<RegistrationStatus> statuses);
    boolean existsByUsernameIgnoreCaseAndStatusIn(String username, List<RegistrationStatus> statuses);
    boolean existsByDocumentHashAndStatusIn(String documentHash, List<RegistrationStatus> statuses);
}