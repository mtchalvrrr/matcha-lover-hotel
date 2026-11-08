package com.hotel.system.settings;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class HotelBrandingAdvice {
    private final HotelSettingsService settings;

    public HotelBrandingAdvice(HotelSettingsService settings) {
        this.settings = settings;
    }

    @ModelAttribute("hotelSettings")
    public HotelSettings hotelSettings() {
        return settings.current();
    }
}
