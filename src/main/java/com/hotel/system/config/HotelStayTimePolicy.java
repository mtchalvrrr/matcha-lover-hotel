package com.hotel.system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

@Component
public class HotelStayTimePolicy {
    private final LocalTime checkInTime;
    private final LocalTime checkOutTime;

    public HotelStayTimePolicy(
            @Value("${hotel.check-in-time:14:00}") LocalTime checkInTime,
            @Value("${hotel.check-out-time:12:00}") LocalTime checkOutTime) {
        if (checkInTime == null || checkOutTime == null) {
            throw new IllegalArgumentException("Hotel check-in and check-out times are required.");
        }
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
    }

    public LocalTime checkInTime() { return checkInTime; }
    public LocalTime checkOutTime() { return checkOutTime; }
}
