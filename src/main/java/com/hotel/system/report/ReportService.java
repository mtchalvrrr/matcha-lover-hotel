package com.hotel.system.report;

import com.hotel.system.payment.Payment;
import com.hotel.system.payment.PaymentRepository;
import com.hotel.system.payment.PaymentStatus;
import com.hotel.system.reservation.Reservation;
import com.hotel.system.reservation.ReservationRepository;
import com.hotel.system.reservation.ReservationSource;
import com.hotel.system.reservation.ReservationStatus;
import com.hotel.system.room.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportService {
    private static final List<ReservationStatus> OCCUPANCY_STATUSES =
            List.of(ReservationStatus.APPROVED, ReservationStatus.CHECKED_IN, ReservationStatus.CHECKED_OUT);

    private final ReservationRepository reservations;
    private final PaymentRepository payments;
    private final RoomRepository rooms;
    private final Clock clock;

    public ReportService(ReservationRepository reservations, PaymentRepository payments,
                         RoomRepository rooms, Clock clock) {
        this.reservations = reservations;
        this.payments = payments;
        this.rooms = rooms;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReportSummary summarize(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("Choose a valid report date range.");
        }
        long periodDays = ChronoUnit.DAYS.between(from, to) + 1;
        if (periodDays > 366) {
            throw new IllegalArgumentException("Report range cannot exceed 366 days.");
        }

        List<Reservation> bookings = reservations.findByCheckInDateBetweenOrderByCheckInDateAsc(from, to);
        List<Reservation> overlappingStays =
                reservations.findByCheckInDateBeforeAndCheckOutDateAfterAndStatusInOrderByCheckInDateAsc(
                        to.plusDays(1), from, OCCUPANCY_STATUSES);

        ZoneId zone = clock.getZone();
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        List<Payment> paidPayments = payments.findByCreatedAtBetweenAndStatusOrderByCreatedAtAsc(
                start, end, PaymentStatus.PAID);
        BigDecimal revenue = paidPayments.stream().map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<LocalDate, BigDecimal> revenueByPaymentDate = paidPayments.stream().collect(Collectors.groupingBy(
                payment -> payment.getCreatedAt().atZone(zone).toLocalDate(),
                LinkedHashMap::new,
                Collectors.reducing(BigDecimal.ZERO, Payment::getAmount, BigDecimal::add)));
        BigDecimal refundedAmount = payments.findByCreatedAtBetweenAndStatusOrderByCreatedAtAsc(
                        start, end, PaymentStatus.REFUNDED)
                .stream().map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstandingBalance = bookings.stream()
                .filter(reservation -> reservation.getStatus() == ReservationStatus.APPROVED
                        || reservation.getStatus() == ReservationStatus.CHECKED_IN)
                .map(reservation -> reservation.getTotalAmount()
                        .subtract(payments.sumPaidForReservation(reservation.getId(), PaymentStatus.PAID))
                        .max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> byRoom = bookings.stream().collect(Collectors.groupingBy(
                reservation -> reservation.getRoomCode() + " — " + reservation.getRoomName(),
                LinkedHashMap::new, Collectors.counting()));
        Map<LocalDate, Long> byCheckInDate = bookings.stream().collect(Collectors.groupingBy(
                Reservation::getCheckInDate, LinkedHashMap::new, Collectors.counting()));

        long reservedRoomNights = overlappingStays.stream()
                .mapToLong(reservation -> occupiedNightsWithin(reservation, from, to))
                .sum();
        long availableRoomNights = rooms.countByActiveTrue() * periodDays;
        BigDecimal bookedCapacityRate = availableRoomNights == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(reservedRoomNights)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(availableRoomNights), 2, RoundingMode.HALF_UP);

        return new ReportSummary(
                from,
                to,
                bookings.size(),
                countStatus(bookings, ReservationStatus.PENDING),
                countStatus(bookings, ReservationStatus.APPROVED),
                countStatus(bookings, ReservationStatus.REJECTED),
                countStatus(bookings, ReservationStatus.CANCELLED),
                bookings.stream().filter(r -> r.getSource() == ReservationSource.ONLINE).count(),
                bookings.stream().filter(r -> r.getSource() == ReservationSource.WALK_IN).count(),
                reservedRoomNights,
                availableRoomNights,
                bookedCapacityRate,
                revenue,
                refundedAmount,
                outstandingBalance,
                byRoom,
                byCheckInDate,
                revenueByPaymentDate
        );
    }

    private long countStatus(List<Reservation> bookings, ReservationStatus status) {
        return bookings.stream().filter(reservation -> reservation.getStatus() == status).count();
    }

    private long occupiedNightsWithin(Reservation reservation, LocalDate from, LocalDate to) {
        LocalDate occupiedFrom = reservation.getCheckInDate().isAfter(from)
                ? reservation.getCheckInDate() : from;
        LocalDate occupiedUntil = reservation.getCheckOutDate().isBefore(to.plusDays(1))
                ? reservation.getCheckOutDate() : to.plusDays(1);
        return Math.max(0, ChronoUnit.DAYS.between(occupiedFrom, occupiedUntil));
    }
}
