package com.hotel.system.reservation;

import com.hotel.system.payment.Payment;
import com.hotel.system.payment.PaymentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a walk-in reservation and its onsite payment as one atomic operation.
 * If payment validation or persistence fails, the reservation is rolled back too.
 */
@Service
@Transactional
public class WalkInBookingService {
    private final ReservationService reservations;
    private final PaymentService payments;

    public WalkInBookingService(ReservationService reservations, PaymentService payments) {
        this.reservations = reservations;
        this.payments = payments;
    }

    public WalkInBookingResult create(String adminUsername, String guestName, String guestEmail,
                                      Long roomId, java.time.LocalDate checkIn,
                                      java.time.LocalDate checkOut, Integer guests,
                                      String paymentMethod) {
        Reservation reservation = reservations.createWalkIn(adminUsername, guestName, guestEmail,
                roomId, checkIn, checkOut, guests);
        Payment payment = payments.recordWalkInPayment(reservation.getId(), paymentMethod, adminUsername);
        return new WalkInBookingResult(reservation, payment);
    }

    public record WalkInBookingResult(Reservation reservation, Payment payment) { }
}
