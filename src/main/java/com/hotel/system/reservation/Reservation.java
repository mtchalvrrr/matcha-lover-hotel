package com.hotel.system.reservation;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "hotel_reservations", indexes = {
    @Index(name = "idx_reservation_room_dates", columnList = "room_id,check_in_date,check_out_date"),
    @Index(name = "idx_reservation_customer", columnList = "customer_id,status")
})
public class Reservation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "customer_id")
    private Long customerId;
    @Column(name = "customer_username", nullable = false, length = 80)
    private String customerUsername;
    @Column(name = "customer_email", nullable = false, length = 254)
    private String customerEmail;
    @Column(name = "guest_name", nullable = false, length = 160)
    private String guestName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationSource source = ReservationSource.ONLINE;
    @Column(name = "room_id", nullable = false)
    private Long roomId;
    @Column(name = "room_code_snapshot", nullable = false, length = 30)
    private String roomCode;
    @Column(name = "room_name_snapshot", nullable = false, length = 100)
    private String roomName;
    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;
    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;
    @Column(nullable = false)
    private Integer guests;
    @Column(nullable = false)
    private Integer nights;
    @Column(name = "nightly_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal nightlyRate;
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.PENDING;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Reservation() {}

    public Reservation(Long customerId, String customerUsername, String customerEmail,
                       Long roomId, String roomCode, String roomName,
                       LocalDate checkInDate, LocalDate checkOutDate, Integer guests,
                       Integer nights, BigDecimal nightlyRate, BigDecimal totalAmount) {
        this.customerId = customerId;
        this.customerUsername = customerUsername;
        this.customerEmail = customerEmail;
        this.guestName = customerUsername;
        this.source = ReservationSource.ONLINE;
        this.roomId = roomId;
        this.roomCode = roomCode;
        this.roomName = roomName;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.guests = guests;
        this.nights = nights;
        this.nightlyRate = nightlyRate;
        this.totalAmount = totalAmount;
        this.status = ReservationStatus.PENDING;
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public String getCustomerUsername() { return customerUsername; }
    public String getCustomerEmail() { return customerEmail; }
    public String getGuestName() { return guestName; }
    public ReservationSource getSource() { return source; }
    public Long getRoomId() { return roomId; }
    public String getRoomCode() { return roomCode; }
    public String getRoomName() { return roomName; }
    public LocalDate getCheckInDate() { return checkInDate; }
    public LocalDate getCheckOutDate() { return checkOutDate; }
    public Integer getGuests() { return guests; }
    public Integer getNights() { return nights; }
    public BigDecimal getNightlyRate() { return nightlyRate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public Reservation(Long roomId, String guestName, String guestEmail, String adminUsername,
                       String roomCode, String roomName, LocalDate checkInDate, LocalDate checkOutDate,
                       Integer guests, Integer nights, BigDecimal nightlyRate, BigDecimal totalAmount) {
        this.customerId = null;
        this.customerUsername = "WALK-IN (" + adminUsername + ")";
        this.customerEmail = guestEmail;
        this.guestName = guestName;
        this.source = ReservationSource.WALK_IN;
        this.roomId = roomId;
        this.roomCode = roomCode;
        this.roomName = roomName;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.guests = guests;
        this.nights = nights;
        this.nightlyRate = nightlyRate;
        this.totalAmount = totalAmount;
        this.status = ReservationStatus.APPROVED;
    }

    public void changeStatus(ReservationStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
}