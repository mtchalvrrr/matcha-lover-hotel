package com.hotel.system.room;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RoomPhotoRepository extends JpaRepository<RoomPhoto, Long> {
    List<RoomPhoto> findByRoomIdOrderByUploadedAtAsc(Long roomId);
    Optional<RoomPhoto> findByIdAndRoomId(Long id, Long roomId);
}