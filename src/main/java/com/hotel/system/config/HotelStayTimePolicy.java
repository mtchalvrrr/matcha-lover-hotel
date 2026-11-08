package com.hotel.system.config;

import com.hotel.system.settings.HotelSettingsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

@Component
public class HotelStayTimePolicy {
    private final HotelSettingsRepository settings;
    private final LocalTime defaultCheckInTime;
    private final LocalTime defaultCheckOutTime;

    public HotelStayTimePolicy(HotelSettingsRepository settings,
            @Value("${hotel.check-in-time:14:00}") LocalTime checkInTime,
            @Value("${hotel.check-out-time:12:00}") LocalTime checkOutTime) {
        this.settings = settings;
        this.defaultCheckInTime = checkInTime;
        this.defaultCheckOutTime = checkOutTime;
    }

    public LocalTime checkInTime() {
        return settings.findById(1L).map(record -> record.getCheckInTime()).orElse(defaultCheckInTime);
    }

    public LocalTime checkOutTime() {
        return settings.findById(1L).map(record -> record.getCheckOutTime()).orElse(defaultCheckOutTime);
    }
}
