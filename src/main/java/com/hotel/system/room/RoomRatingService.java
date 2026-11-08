package com.hotel.system.room;

import com.hotel.system.account.SystemUser;
import com.hotel.system.account.SystemUserRepository;
import com.hotel.system.reservation.Reservation;
import com.hotel.system.reservation.ReservationRepository;
import com.hotel.system.reservation.ReservationSource;
import com.hotel.system.reservation.ReservationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class RoomRatingService {
    private final RoomRatingRepository ratings;
    private final ReservationRepository reservations;
    private final SystemUserRepository users;

    public RoomRatingService(RoomRatingRepository ratings, ReservationRepository reservations,
                             SystemUserRepository users) {
        this.ratings = ratings;
        this.reservations = reservations;
        this.users = users;
    }

    public RoomRating rateCompletedStay(Long reservationId, String username, int stars, String reviewText) {
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("Choose a rating from 1 to 5 stars.");
        }
        SystemUser customer = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Customer account was not found."));
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation was not found."));

        if (reservation.getSource() != ReservationSource.ONLINE
                || !Objects.equals(reservation.getCustomerId(), customer.getId())) {
            throw new IllegalArgumentException("You can only rate your own completed reservation.");
        }
        if (reservation.getStatus() != ReservationStatus.CHECKED_OUT
                || reservation.getCheckedOutAt() == null) {
            throw new IllegalStateException("You can rate a room after your stay has been checked out.");
        }

        RoomRating rating = ratings.findByReservationId(reservationId)
                .orElseGet(() -> new RoomRating(reservation.getRoomId(), reservationId,
                        customer.getId(), customer.getUsername(), stars));
        rating.setStars(stars);
        rating.setReviewText(reviewText);
        return ratings.save(rating);
    }

    @Transactional(readOnly = true)
    public List<RoomRating> feedbackForRoom(Long roomId) {
        return ratings.findByRoomIdAndReviewTextIsNotNullOrderByCreatedAtDesc(roomId);
    }

    @Transactional(readOnly = true)
    public Map<Long, RoomRatingSummary> summaries(Collection<Long> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) return Map.of();
        Map<Long, List<RoomRating>> grouped = ratings.findByRoomIdIn(roomIds).stream()
                .collect(Collectors.groupingBy(RoomRating::getRoomId));
        Map<Long, RoomRatingSummary> result = new LinkedHashMap<>();
        for (Long roomId : roomIds) {
            List<RoomRating> roomRatings = grouped.getOrDefault(roomId, List.of());
            double average = roomRatings.stream().mapToInt(RoomRating::getStars).average().orElse(0.0);
            result.put(roomId, new RoomRatingSummary(average, roomRatings.size()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<Long, RoomRating> ratingsForReservations(Collection<Long> reservationIds) {
        if (reservationIds == null || reservationIds.isEmpty()) return Map.of();
        return ratings.findByReservationIdIn(reservationIds).stream()
                .collect(Collectors.toMap(RoomRating::getReservationId, rating -> rating));
    }
}
