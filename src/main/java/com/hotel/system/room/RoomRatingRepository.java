package com.hotel.system.room;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomRatingRepository extends JpaRepository<RoomRating, Long> {
    Optional<RoomRating> findByReservationId(Long reservationId);
    List<RoomRating> findByRoomIdIn(Collection<Long> roomIds);
    List<RoomRating> findByReservationIdIn(Collection<Long> reservationIds);
}
