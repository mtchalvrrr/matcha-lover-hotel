package com.hotel.system.room;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findAllByOrderByRoomCodeAsc();
    List<Room> findByActiveTrueOrderByRoomCodeAsc();
    Optional<Room> findByIdAndActiveTrue(Long id);
    boolean existsByRoomCodeIgnoreCase(String roomCode);
    boolean existsByRoomCodeIgnoreCaseAndIdNot(String roomCode, Long id);
    long countByActiveTrue();
}