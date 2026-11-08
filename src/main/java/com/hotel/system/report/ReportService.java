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
import java.util.Set;
import java.util.Objects;
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
        // Preserve the original receipt in payment-date reports even if it is refunded later.
        // Refunds are reported separately using refundedAt.
        List<Payment> paidPayments = payments.findByCreatedAtBetweenAndStatusInOrderByCreatedAtAsc(
                start, end, List.of(PaymentStatus.PAID, PaymentStatus.REFUNDED));
        BigDecimal revenue = paidPayments.stream().map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<LocalDate, BigDecimal> revenueByPaymentDate = paidPayments.stream().collect(Collectors.groupingBy(
                payment -> payment.getCreatedAt().atZone(zone).toLocalDate(),
                LinkedHashMap::new,
                Collectors.reducing(BigDecimal.ZERO, Payment::getAmount, BigDecimal::add)));
        BigDecimal refundedAmount = payments.findByRefundedAtBetweenOrderByRefundedAtAsc(start, end)
                .stream().filter(payment -> payment.getStatus() == PaymentStatus.REFUNDED)
                .map(Payment::getAmount)
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
                LinkedHashMap::new, Collectors.counting())).entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (first, second) -> first, LinkedHashMap::new));
        Map<LocalDate, Long> byCheckInDate = bookings.stream().collect(Collectors.groupingBy(
                Reservation::getCheckInDate, LinkedHashMap::new, Collectors.counting()));
        List<Reservation> requestsCreated = reservations.findByCreatedAtBetweenOrderByCreatedAtAsc(start, end);
        Map<LocalDate, Long> byCreatedDate = requestsCreated.stream()
                .filter(reservation -> reservation.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        reservation -> reservation.getCreatedAt().atZone(zone).toLocalDate(),
                        LinkedHashMap::new, Collectors.counting()));

        Set<Long> customerIds = bookings.stream()
                .filter(reservation -> reservation.getSource() == ReservationSource.ONLINE)
                .map(Reservation::getCustomerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<Reservation> customerHistory = customerIds.isEmpty()
                ? List.of() : reservations.findByCustomerIdIn(customerIds);
        Set<Long> returningCustomerIds = customerHistory.stream()
                .filter(reservation -> reservation.getCustomerId() != null)
                .filter(reservation -> reservation.getCheckInDate() != null
                        && reservation.getCheckInDate().isBefore(from))
                .filter(reservation -> reservation.getStatus() == ReservationStatus.APPROVED
                        || reservation.getStatus() == ReservationStatus.CHECKED_IN
                        || reservation.getStatus() == ReservationStatus.CHECKED_OUT)
                .map(Reservation::getCustomerId)
                .collect(Collectors.toSet());
        long uniqueCustomers = customerIds.size();
        long returningCustomers = returningCustomerIds.size();
        long newCustomers = uniqueCustomers - returningCustomers;

        long reservedRoomNights = overlappingStays.stream()
                .mapToLong(reservation -> occupiedNightsWithin(reservation, from, to, zone))
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
                countStatus(bookings, ReservationStatus.EXPIRED),
                countStatus(bookings, ReservationStatus.NO_SHOW),
                countStatus(bookings, ReservationStatus.CHECKED_IN),
                countStatus(bookings, ReservationStatus.CHECKED_OUT),
                bookings.stream().filter(r -> r.getSource() == ReservationSource.ONLINE).count(),
                bookings.stream().filter(r -> r.getSource() == ReservationSource.WALK_IN).count(),
                uniqueCustomers,
                newCustomers,
                returningCustomers,
                reservedRoomNights,
                availableRoomNights,
                bookedCapacityRate,
                revenue,
                refundedAmount,
                outstandingBalance,
                byRoom,
                byCheckInDate,
                byCreatedDate,
                revenueByPaymentDate
        );
    }

    private long countStatus(List<Reservation> bookings, ReservationStatus status) {
        return bookings.stream().filter(reservation -> reservation.getStatus() == status).count();
    }

    private long occupiedNightsWithin(Reservation reservation, LocalDate from, LocalDate to, ZoneId zone) {
        LocalDate actualCheckIn = reservation.getCheckedInAt() == null
                ? reservation.getCheckInDate()
                : reservation.getCheckedInAt().atZone(zone).toLocalDate();
        LocalDate actualCheckOut = reservation.getCheckedOutAt() == null
                ? reservation.getCheckOutDate()
                : reservation.getCheckedOutAt().atZone(zone).toLocalDate();
        LocalDate occupiedFrom = actualCheckIn.isAfter(from) ? actualCheckIn : from;
        LocalDate occupiedUntil = actualCheckOut.isBefore(to.plusDays(1))
                ? actualCheckOut : to.plusDays(1);
        return Math.max(0, ChronoUnit.DAYS.between(occupiedFrom, occupiedUntil));
    }
}
