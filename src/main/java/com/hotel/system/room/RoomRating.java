package com.hotel.system.room;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "room_ratings", uniqueConstraints = {
    @UniqueConstraint(name = "uk_room_rating_reservation", columnNames = "reservation_id")
}, indexes = {
    @Index(name = "idx_room_rating_room", columnList = "room_id")
})
public class RoomRating {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_username", nullable = false, length = 80)
    private String customerUsername;

    @Column(nullable = false)
    private int stars;

    @Column(name = "review_text", length = 1000)
    private String reviewText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected RoomRating() {}

    public RoomRating(Long roomId, Long reservationId, Long customerId,
                      String customerUsername, int stars) {
        this.roomId = roomId;
        this.reservationId = reservationId;
        this.customerId = customerId;
        this.customerUsername = customerUsername;
        setStars(stars);
    }

    public Long getId() { return id; }
    public Long getRoomId() { return roomId; }
    public Long getReservationId() { return reservationId; }
    public Long getCustomerId() { return customerId; }
    public String getCustomerUsername() { return customerUsername; }
    public int getStars() { return stars; }
    public String getReviewText() { return reviewText; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setReviewText(String reviewText) {
        String cleaned = reviewText == null ? "" : reviewText.trim();
        if (cleaned.length() > 1000) throw new IllegalArgumentException("Feedback must be 1,000 characters or fewer.");
        this.reviewText = cleaned.isEmpty() ? null : cleaned;
        this.updatedAt = Instant.now();
    }

    public void setStars(int stars) {
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("Choose a rating from 1 to 5 stars.");
        }
        this.stars = stars;
        this.updatedAt = Instant.now();
    }
}
