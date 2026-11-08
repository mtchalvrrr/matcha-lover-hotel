package com.hotel.system.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<Reservation> findAllByOrderByCreatedAtDesc();
    List<Reservation> findByCheckInDateBetweenOrderByCheckInDateAsc(LocalDate from, LocalDate to);

    @Query("select case when count(r) > 0 then true else false end from Reservation r " +
           "where r.roomId = :roomId and r.status in :statuses " +
           "and r.checkInDate < :checkOut and r.checkOutDate > :checkIn " +
           "and (:excludeId is null or r.id <> :excludeId)")
    boolean existsOverlappingReservation(@Param("roomId") Long roomId,
        @Param("statuses") Collection<ReservationStatus> statuses,
        @Param("checkIn") LocalDate checkIn, @Param("checkOut") LocalDate checkOut,
        @Param("excludeId") Long excludeId);
}