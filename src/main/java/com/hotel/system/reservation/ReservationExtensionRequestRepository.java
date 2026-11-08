package com.hotel.system.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReservationExtensionRequestRepository extends JpaRepository<ReservationExtensionRequest, Long> {
    boolean existsByReservationIdAndStatus(Long reservationId, ExtensionRequestStatus status);
    List<ReservationExtensionRequest> findByCustomerUsernameOrderByCreatedAtDesc(String customerUsername);
    List<ReservationExtensionRequest> findAllByOrderByCreatedAtDesc();
}
