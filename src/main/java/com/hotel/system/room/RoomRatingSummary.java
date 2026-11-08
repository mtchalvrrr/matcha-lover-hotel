package com.hotel.system.room;

public record RoomRatingSummary(double average, long count) {
    public static RoomRatingSummary empty() {
        return new RoomRatingSummary(0.0, 0);
    }
}
