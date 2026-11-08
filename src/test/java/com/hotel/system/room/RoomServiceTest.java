package com.hotel.system.room;

import com.hotel.system.audit.AuditService;
import com.hotel.system.config.HotelStayTimePolicy;
import com.hotel.system.reservation.ReservationRepository;
import com.hotel.system.reservation.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomServiceTest {
    @Test
    void refusesToReduceCapacityBelowGuestsOnActiveReservations() {
        RoomRepository rooms = mock(RoomRepository.class);
        AuditService audit = mock(AuditService.class);
        ReservationRepository reservations = mock(ReservationRepository.class);
        HotelStayTimePolicy stayTimes = mock(HotelStayTimePolicy.class);
        Room existing = new Room();
        existing.setRoomCode("R-04");
        existing.setName("Family Room");
        existing.setRoomType("Family");
        existing.setCapacity(4);

        Room update = new Room();
        update.setRoomCode("R-05");
        update.setName("Standard Room");
        update.setRoomType("Standard");
        update.setCapacity(2);

        when(rooms.findById(4L)).thenReturn(Optional.of(existing));
        when(rooms.existsByRoomCodeIgnoreCaseAndIdNot("R-05", 4L)).thenReturn(false);
        when(reservations.existsReservationExceedingCapacity(
                4L,
                List.of(ReservationStatus.PENDING, ReservationStatus.APPROVED, ReservationStatus.CHECKED_IN),
                2)).thenReturn(true);

        RoomService service = new RoomService(rooms, audit, reservations,
                Clock.fixed(Instant.parse("2026-11-08T04:00:00Z"), ZoneId.of("Asia/Manila")), stayTimes);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.update(4L, update, "admin"));

        assertTrue(error.getMessage().contains("cannot be reduced"));
        assertEquals("R-04", existing.getRoomCode());
        assertEquals(4, existing.getCapacity());
        verify(audit, never()).record(anyString(), anyString(), anyString(), anyString(), anyString());
    }
    @Test
    void operationalStatusChangeIsAuditedAndRoomIsNoLongerBookable() {
        RoomRepository rooms = mock(RoomRepository.class);
        AuditService audit = mock(AuditService.class);
        ReservationRepository reservations = mock(ReservationRepository.class);
        HotelStayTimePolicy stayTimes = mock(HotelStayTimePolicy.class);
        Room room = new Room();
        ReflectionTestUtils.setField(room, "id", 12L);
        when(rooms.findById(12L)).thenReturn(Optional.of(room));

        RoomService service = new RoomService(rooms, audit, reservations,
                Clock.fixed(Instant.parse("2026-11-08T04:00:00Z"), ZoneId.of("Asia/Manila")), stayTimes);

        service.updateOperationalStatus(12L, CleaningStatus.INSPECTION_REQUIRED,
                MaintenanceStatus.AVAILABLE, "admin");

        assertEquals(CleaningStatus.INSPECTION_REQUIRED, room.getCleaningStatus());
        assertFalse(room.isBookable());
        verify(audit).record(eq("admin"), eq("ROOM_OPERATIONAL_STATUS_UPDATED"), eq("ROOM"),
                eq("12"), contains("Previous: cleaning=READY"));
    }

    @Test
    void roomUnderMaintenanceCannotBeBooked() {
        Room room = new Room();
        room.updateOperationalStatus(CleaningStatus.READY, MaintenanceStatus.UNDER_MAINTENANCE, "admin");
        assertFalse(room.isBookable());
    }

}
