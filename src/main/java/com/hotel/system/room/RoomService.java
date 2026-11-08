package com.hotel.system.room;

import com.hotel.system.audit.AuditService;
import com.hotel.system.config.HotelStayTimePolicy;
import com.hotel.system.reservation.ReservationRepository;
import com.hotel.system.reservation.ReservationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@Transactional
public class RoomService {
    private final RoomRepository rooms;
    private final AuditService audit;
    private final ReservationRepository reservations;
    private final Clock clock;
    private final HotelStayTimePolicy stayTimes;
    private static final List<ReservationStatus> BLOCKING_STATUSES =
            List.of(ReservationStatus.PENDING, ReservationStatus.APPROVED, ReservationStatus.CHECKED_IN);

    public RoomService(RoomRepository rooms, AuditService audit, ReservationRepository reservations,
                       Clock clock, HotelStayTimePolicy stayTimes) {
        this.rooms = rooms;
        this.audit = audit;
        this.reservations = reservations;
        this.clock = clock;
        this.stayTimes = stayTimes;
    }

    @Transactional(readOnly = true)
    public List<Room> publicRooms() {
        return rooms.findByActiveTrueOrderByRoomCodeAsc();
    }

    @Transactional(readOnly = true)
    public List<Room> searchAvailable(LocalDate checkIn, LocalDate checkOut, Integer guests) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("Choose valid check-in and check-out dates.");
        }
        if (checkIn.atTime(stayTimes.checkInTime()).isBefore(LocalDateTime.now(clock))) {
            throw new IllegalArgumentException("Check-in date and time cannot be in the past.");
        }
        if (guests == null || guests < 1 || guests > 20) {
            throw new IllegalArgumentException("Guest count must be between 1 and 20.");
        }
        return rooms.findByActiveTrueOrderByRoomCodeAsc().stream()
                .filter(room -> room.getCapacity() >= guests)
                .filter(room -> !reservations.existsOverlappingReservation(
                        room.getId(), BLOCKING_STATUSES, checkIn, checkOut, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isAvailable(Long roomId, LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)
                || checkIn.atTime(stayTimes.checkInTime()).isBefore(LocalDateTime.now(clock))) return false;
        return !reservations.existsOverlappingReservation(
                roomId, BLOCKING_STATUSES, checkIn, checkOut, null);
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
        if (form.getCapacity() == null || form.getCapacity() < 1) {
            throw new IllegalArgumentException("Room capacity must be at least one guest.");
        }
        if (reservations.existsReservationExceedingCapacity(id, BLOCKING_STATUSES, form.getCapacity())) {
            throw new IllegalStateException(
                    "Room capacity cannot be reduced below the guest count of a pending, approved, or checked-in reservation.");
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