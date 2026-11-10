package com.hotel.system.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ReservationExtensionRequestRepository
        extends JpaRepository<ReservationExtensionRequest, Long> {
    List<ReservationExtensionRequest> findAllByOrderByCreatedAtDesc();
    List<ReservationExtensionRequest> findByCustomerUsernameOrderByCreatedAtDesc(String username);
    List<ReservationExtensionRequest> findByStatusOrderByCreatedAtAsc(ReservationExtensionRequest.Status status);
    Optional<ReservationExtensionRequest> findFirstByReservationIdAndStatusInOrderByCreatedAtDesc(
            Long reservationId, java.util.Collection<ReservationExtensionRequest.Status> statuses);
}