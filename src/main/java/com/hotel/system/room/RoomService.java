package com.hotel.system.room;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class RoomService {
    private final RoomRepository rooms;

    public RoomService(RoomRepository rooms) {
        this.rooms = rooms;
    }

    @Transactional(readOnly = true)
    public List<Room> publicRooms() {
        return rooms.findByActiveTrueOrderByRoomCodeAsc();
    }

    @Transactional(readOnly = true)
    public List<Room> allRooms() {
        return rooms.findAllByOrderByRoomCodeAsc();
    }

    @Transactional(readOnly = true)
    public Room getPublicRoom(Long id) {
        return rooms.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new RoomNotFoundException("Room not found."));
    }

    @Transactional(readOnly = true)
    public Room getRoom(Long id) {
        return rooms.findById(id)
                .orElseThrow(() -> new RoomNotFoundException("Room not found."));
    }

    public Room create(Room form) {
        String code = form.getRoomCode().trim();
        if (rooms.existsByRoomCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("A room with this room code already exists.");
        }
        Room room = new Room();
        room.updateFrom(form);
        return rooms.save(room);
    }

    public Room update(Long id, Room form) {
        Room room = getRoom(id);
        if (rooms.existsByRoomCodeIgnoreCaseAndIdNot(form.getRoomCode().trim(), id)) {
            throw new IllegalArgumentException("A room with this room code already exists.");
        }
        room.updateFrom(form);
        return room;
    }

    public void setActive(Long id, boolean active) {
        Room room = getRoom(id);
        if (active) room.activate(); else room.deactivate();
    }
}