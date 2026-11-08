package com.hotel.system.room;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates the initial room inventory for a fresh database.
 * Existing room records are never overwritten or duplicated.
 */
@Component
@Order(20)
public class RoomDataSeeder implements CommandLineRunner {
    private final RoomRepository rooms;

    public RoomDataSeeder(RoomRepository rooms) {
        this.rooms = rooms;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (rooms.count() > 0) {
            return;
        }

        List<Room> starterRooms = new ArrayList<>();
        addType(starterRooms, "Standard", "Standard Room", 100, 4, 2, "1800.00",
                "A comfortable standard room for up to two guests.");
        addType(starterRooms, "Deluxe", "Deluxe Room", 200, 4, 2, "2600.00",
                "A spacious deluxe room for up to two guests.");
        addType(starterRooms, "Suite", "Suite Room", 300, 4, 2, "4200.00",
                "A suite room designed for a comfortable stay for up to two guests.");
        addType(starterRooms, "Family", "Family Room", 400, 4, 4, "3500.00",
                "A family room with space for up to four guests.");

        rooms.saveAll(starterRooms);
    }

    private void addType(List<Room> target, String type, String displayName, int firstCode,
                         int count, int capacity, String nightlyRate, String description) {
        for (int i = 1; i <= count; i++) {
            Room room = new Room();
            room.setRoomCode(Integer.toString(firstCode + i));
            room.setName(displayName + " " + i);
            room.setRoomType(type);
            room.setDescription(description);
            room.setCapacity(capacity);
            room.setNightlyRate(new BigDecimal(nightlyRate));
            room.setAmenities("");
            // Room defaults: active, housekeeping READY, maintenance AVAILABLE.
            target.add(room);
        }
    }
}
