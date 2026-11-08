package com.hotel.system.room;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "hotel_rooms", uniqueConstraints = @UniqueConstraint(name = "uk_hotel_rooms_code", columnNames = "room_code"))
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "room_code", nullable = false, length = 30)
    private String roomCode;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String name;

    @NotBlank
    @Column(nullable = false, length = 60)
    private String roomType;

    @Column(length = 2000)
    private String description;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer capacity = 2;

    @NotNull
    @DecimalMin("0.01")
    @Column(name = "nightly_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal nightlyRate = new BigDecimal("1000.00");

    @Column(length = 1000)
    private String amenities;

    @Column(name = "cover_image_path", length = 500)
    private String coverImagePath;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Room() {}

    public Long getId() { return id; }
    public String getRoomCode() { return roomCode; }
    public String getName() { return name; }
    public String getRoomType() { return roomType; }
    public String getDescription() { return description; }
    public Integer getCapacity() { return capacity; }
    public BigDecimal getNightlyRate() { return nightlyRate; }
    public String getAmenities() { return amenities; }
    public String getCoverImagePath() { return coverImagePath; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }
    public void setName(String name) { this.name = name; }
    public void setRoomType(String roomType) { this.roomType = roomType; }
    public void setDescription(String description) { this.description = description; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public void setNightlyRate(BigDecimal nightlyRate) { this.nightlyRate = nightlyRate; }
    public void setAmenities(String amenities) { this.amenities = amenities; }
    public void setCoverImagePath(String coverImagePath) { this.coverImagePath = coverImagePath; this.updatedAt = Instant.now(); }

    public void updateFrom(Room form) {
        this.roomCode = form.roomCode.trim();
        this.name = form.name.trim();
        this.roomType = form.roomType.trim();
        this.description = form.description;
        this.capacity = form.capacity;
        this.nightlyRate = form.nightlyRate;
        this.amenities = form.amenities;
        if (form.coverImagePath != null && !form.coverImagePath.isBlank()) this.coverImagePath = form.coverImagePath;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }
}