package com.hotel.system.room;

import com.hotel.system.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class RoomService {
    private final RoomRepository rooms;
    private final AuditService audit;

    public RoomService(RoomRepository rooms, AuditService audit) {
        this.rooms = rooms;
        this.audit = audit;
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

    public Room create(Room form, String actor) {
        String code = form.getRoomCode().trim();
        if (rooms.existsByRoomCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("A room with this room code already exists.");
        }
        Room room = new Room();
        room.updateFrom(form);
        Room saved = rooms.save(room);
        audit.record(actor, "ROOM_CREATED", "ROOM", saved.getId().toString(), "Created room " + saved.getRoomCode());
        return saved;
    }

    public Room update(Long id, Room form, String actor) {
        Room room = getRoom(id);
        if (rooms.existsByRoomCodeIgnoreCaseAndIdNot(form.getRoomCode().trim(), id)) {
            throw new IllegalArgumentException("A room with this room code already exists.");
        }
        room.updateFrom(form);
        audit.record(actor, "ROOM_UPDATED", "ROOM", id.toString(), "Updated room " + room.getRoomCode());
        return room;
    }

    public void setActive(Long id, boolean active, String actor) {
        Room room = getRoom(id);
        if (active) room.activate(); else room.deactivate();
        audit.record(actor, active ? "ROOM_ACTIVATED" : "ROOM_DEACTIVATED", "ROOM", id.toString(),
            "Changed availability of room " + room.getRoomCode());
    }
}