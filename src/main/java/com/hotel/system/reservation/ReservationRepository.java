package com.hotel.system.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<Reservation> findAllByOrderByCreatedAtDesc();
    boolean existsByRoomIdAndStatusInAndCheckInDateLessThanAndCheckOutDateGreaterThan(
        Long roomId, Collection<ReservationStatus> statuses, LocalDate checkOutDate, LocalDate checkInDate);
}