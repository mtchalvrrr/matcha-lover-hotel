package com.hotel.system.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReservationExtensionRequestRepository extends JpaRepository<ReservationExtensionRequest, Long> {
    List<ReservationExtensionRequest> findByReservationIdOrderByRequestedAtDesc(Long reservationId);
    List<ReservationExtensionRequest> findByCustomerUsernameOrderByRequestedAtDesc(String customerUsername);
    List<ReservationExtensionRequest> findAllByOrderByRequestedAtDesc();
    boolean existsByReservationIdAndStatus(Long reservationId, ExtensionRequestStatus status);
}
