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
    BigDecimal paidRevenue,
    Map<String, Long> bookingsByRoom
) {}