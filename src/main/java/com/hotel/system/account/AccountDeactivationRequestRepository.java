package com.hotel.system.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountDeactivationRequestRepository extends JpaRepository<AccountDeactivationRequest, Long> {
    boolean existsByUserIdAndStatus(Long userId, DeactivationRequestStatus status);
    List<AccountDeactivationRequest> findByStatusOrderByRequestedAtAsc(DeactivationRequestStatus status);
    List<AccountDeactivationRequest> findByUsernameOrderByRequestedAtDesc(String username);
}
