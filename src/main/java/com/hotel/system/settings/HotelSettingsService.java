package com.hotel.system.settings;

import com.hotel.system.audit.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

@Service
@Transactional
public class HotelSettingsService {
    private final HotelSettingsRepository settings;
    private final AuditService audit;
    private final String defaultName;
    private final String defaultAddress;
    private final String defaultContact;
    private final LocalTime defaultCheckIn;
    private final LocalTime defaultCheckOut;

    public HotelSettingsService(HotelSettingsRepository settings, AuditService audit,
            @Value("${hotel.name:[HOTEL NAME]}") String defaultName,
            @Value("${hotel.address:[HOTEL ADDRESS]}") String defaultAddress,
            @Value("${hotel.contact:[HOTEL CONTACT INFORMATION]}") String defaultContact,
            @Value("${hotel.check-in-time:14:00}") LocalTime defaultCheckIn,
            @Value("${hotel.check-out-time:12:00}") LocalTime defaultCheckOut) {
        this.settings = settings;
        this.audit = audit;
        this.defaultName = defaultName;
        this.defaultAddress = defaultAddress;
        this.defaultContact = defaultContact;
        this.defaultCheckIn = defaultCheckIn;
        this.defaultCheckOut = defaultCheckOut;
    }

    @Transactional(readOnly = true)
    public HotelSettings current() {
        return settings.findById(1L).orElseGet(() -> new HotelSettings(
                defaultName, defaultAddress, defaultContact, defaultCheckIn, defaultCheckOut,
                "Please contact the hotel for the current cancellation and refund policy."));
    }

    public HotelSettings update(String actor, String name, String address, String contact,
                                LocalTime checkIn, LocalTime checkOut, String cancellationPolicy) {
        String cleanName = clean(name);
        String cleanAddress = clean(address);
        String cleanContact = clean(contact);
        String cleanPolicy = clean(cancellationPolicy);
        if (cleanName.isBlank() || cleanName.length() > 120) {
            throw new IllegalArgumentException("Hotel name is required and must be 120 characters or fewer.");
        }
        if (cleanAddress.length() > 500 || cleanContact.length() > 500) {
            throw new IllegalArgumentException("Address and contact information must be 500 characters or fewer.");
        }
        if (checkIn == null || checkOut == null) {
            throw new IllegalArgumentException("Choose valid check-in and check-out times.");
        }
        if (cleanPolicy.length() > 3000) {
            throw new IllegalArgumentException("Cancellation policy must be 3,000 characters or fewer.");
        }

        HotelSettings record = settings.findById(1L).orElseGet(() ->
                new HotelSettings(defaultName, defaultAddress, defaultContact,
                        defaultCheckIn, defaultCheckOut, cleanPolicy));
        record.update(cleanName, cleanAddress, cleanContact, checkIn, checkOut, cleanPolicy, actor);
        HotelSettings saved = settings.save(record);
        audit.record(actor, "HOTEL_SETTINGS_UPDATED", "HOTEL_SETTINGS", "1",
                "Updated hotel profile, stay times, and cancellation policy.");
        return saved;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
