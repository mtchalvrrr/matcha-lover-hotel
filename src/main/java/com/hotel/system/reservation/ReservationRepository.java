package com.hotel.system.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<Reservation> findByRoomIdAndStatusInOrderByCheckInDateAsc(Long roomId, Collection<ReservationStatus> statuses);
    List<Reservation> findByCustomerIdIn(Collection<Long> customerIds);
    List<Reservation> findAllByOrderByCreatedAtDesc();
    List<Reservation> findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(ReservationStatus status, java.time.Instant cutoff);
    List<Reservation> findByStatusAndCheckInDateLessThanEqualOrderByCheckInDateAsc(ReservationStatus status, LocalDate checkInDate);
    long countByStatus(ReservationStatus status);
    long countByCheckInDateAndStatusIn(LocalDate checkInDate, Collection<ReservationStatus> statuses);
    long countByCheckOutDateAndStatusIn(LocalDate checkOutDate, Collection<ReservationStatus> statuses);
    long countByCheckedInAtBetween(java.time.Instant from, java.time.Instant to);
    long countByCheckedOutAtBetween(java.time.Instant from, java.time.Instant to);
    long countByCustomerUsernameAndStatus(String username, ReservationStatus status);
    long countByCustomerUsernameAndStatusIn(String username, Collection<ReservationStatus> statuses);

    @Query("select case when count(r) > 0 then true else false end from Reservation r " +
           "where r.roomId = :roomId and r.status in :statuses and r.guests > :capacity")
    boolean existsReservationExceedingCapacity(@Param("roomId") Long roomId,
            @Param("statuses") Collection<ReservationStatus> statuses,
            @Param("capacity") Integer capacity);

    @Query("select r from Reservation r where " +
           "(:query is null or lower(r.guestName) like lower(concat('%', :query, '%')) " +
           "or lower(r.customerUsername) like lower(concat('%', :query, '%')) " +
           "or lower(r.customerEmail) like lower(concat('%', :query, '%')) " +
           "or lower(r.roomCode) like lower(concat('%', :query, '%')) " +
           "or cast(r.id as string) like concat('%', :query, '%')) " +
           "and (:status is null or r.status = :status) " +
           "and (:source is null or r.source = :source) " +
           "and (:fromDate is null or r.checkInDate >= :fromDate) " +
           "and (:toDate is null or r.checkInDate <= :toDate) " +
           "order by r.createdAt desc")
    List<Reservation> searchReservations(@Param("query") String query,
            @Param("status") ReservationStatus status,
            @Param("source") ReservationSource source,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
    List<Reservation> findByCheckInDateBetweenOrderByCheckInDateAsc(LocalDate from, LocalDate to);
    List<Reservation> findByCreatedAtBetweenOrderByCreatedAtAsc(java.time.Instant from, java.time.Instant to);
    List<Reservation> findByCheckInDateBetweenAndStatusInOrderByCheckInDateAsc(
            LocalDate from, LocalDate to, Collection<ReservationStatus> statuses);
    List<Reservation> findByCheckOutDateBetweenAndStatusInOrderByCheckOutDateAsc(
            LocalDate from, LocalDate to, Collection<ReservationStatus> statuses);
    List<Reservation> findByCheckInDateBeforeAndCheckOutDateAfterAndStatusInOrderByCheckInDateAsc(
        LocalDate checkOutExclusive, LocalDate checkInExclusive, Collection<ReservationStatus> statuses);

    @Query("select r from Reservation r where r.status in :statuses and r.checkedInAt is not null " +
           "and r.checkedInAt < :endInstant " +
           "and ((r.checkedOutAt is null and r.checkOutDate > :fromDate) or r.checkedOutAt > :startInstant) " +
           "order by r.checkedInAt asc")
    List<Reservation> findActualStaysOverlapping(@Param("statuses") Collection<ReservationStatus> statuses,
            @Param("endInstant") java.time.Instant endInstant,
            @Param("fromDate") LocalDate fromDate,
            @Param("startInstant") java.time.Instant startInstant);

    @Query("select case when count(r) > 0 then true else false end from Reservation r " +
           "where r.roomId = :roomId and r.status in :statuses " +
           "and r.checkInDate < :checkOut and r.checkOutDate > :checkIn " +
           "and (:excludeId is null or r.id <> :excludeId)")
    boolean existsOverlappingReservation(@Param("roomId") Long roomId,
        @Param("statuses") Collection<ReservationStatus> statuses,
        @Param("checkIn") LocalDate checkIn, @Param("checkOut") LocalDate checkOut,
        @Param("excludeId") Long excludeId);
}
