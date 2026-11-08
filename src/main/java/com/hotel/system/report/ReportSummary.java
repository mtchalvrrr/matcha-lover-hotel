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
    long onlineReservations,
    long walkInReservations,
    long reservedRoomNights,
    long availableRoomNights,
    BigDecimal bookedCapacityRate,
    BigDecimal paidRevenue,
    BigDecimal refundedAmount,
    BigDecimal outstandingBalance,
    Map<String, Long> bookingsByRoom,
    Map<LocalDate, Long> bookingsByCheckInDate
) {}
