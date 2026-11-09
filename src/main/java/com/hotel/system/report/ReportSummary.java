package com.hotel.system.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record ReportSummary(
    LocalDate fromDate,
    LocalDate toDate,
    long totalReservations,
    long pendingReservations,
    long approvedReservations,
    long rejectedReservations,
    long cancelledReservations,
    long expiredReservations,
    long voidReservations,
    long noShowReservations,
    long checkedInReservations,
    long checkedOutReservations,
    long onlineReservations,
    long walkInReservations,
    long uniqueCustomers,
    long newCustomers,
    long returningCustomers,
    long reservedRoomNights,
    long availableRoomNights,
    BigDecimal bookedCapacityRate,
    long actualOccupiedRoomNights,
    BigDecimal actualOccupancyRate,
    BigDecimal paidRevenue,
    BigDecimal refundedAmount,
    BigDecimal outstandingBalance,
    Map<String, Long> bookingsByRoom,
    Map<LocalDate, Long> bookingsByCheckInDate,
    Map<LocalDate, Long> bookingsByActualStayDate,
    Map<LocalDate, Long> bookingsByCreatedDate,
    Map<LocalDate, BigDecimal> revenueByPaymentDate,
    Map<String, BigDecimal> roomRevenue
) {}
