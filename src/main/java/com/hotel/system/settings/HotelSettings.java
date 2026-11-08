package com.hotel.system.settings;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "hotel_settings")
public class HotelSettings {
    @Id
    private Long id = 1L;

    @Column(name = "hotel_name", nullable = false, length = 120)
    private String hotelName;

    @Column(length = 500)
    private String address;

    @Column(name = "contact_information", length = 500)
    private String contactInformation;

    @Column(name = "check_in_time", nullable = false)
    private LocalTime checkInTime = LocalTime.of(14, 0);

    @Column(name = "check_out_time", nullable = false)
    private LocalTime checkOutTime = LocalTime.NOON;

    @Column(name = "cancellation_policy", length = 3000)
    private String cancellationPolicy;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected HotelSettings() { }

    public HotelSettings(String hotelName, String address, String contactInformation,
                         LocalTime checkInTime, LocalTime checkOutTime, String cancellationPolicy) {
        this.id = 1L;
        this.hotelName = hotelName;
        this.address = address;
        this.contactInformation = contactInformation;
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
        this.cancellationPolicy = cancellationPolicy;
    }

    public Long getId() { return id; }
    public String getHotelName() { return hotelName; }
    public String getAddress() { return address; }
    public String getContactInformation() { return contactInformation; }
    public LocalTime getCheckInTime() { return checkInTime; }
    public LocalTime getCheckOutTime() { return checkOutTime; }
    public String getCancellationPolicy() { return cancellationPolicy; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String hotelName, String address, String contactInformation,
                       LocalTime checkInTime, LocalTime checkOutTime,
                       String cancellationPolicy, String actor) {
        this.hotelName = hotelName;
        this.address = address;
        this.contactInformation = contactInformation;
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
        this.cancellationPolicy = cancellationPolicy;
        this.updatedBy = actor;
        this.updatedAt = Instant.now();
    }
}
