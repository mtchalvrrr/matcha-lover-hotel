package com.hotel.system.report;

import com.hotel.system.payment.*;
import com.hotel.system.reservation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportService {
    private final ReservationRepository reservations;
    private final PaymentRepository payments;
    private final Clock clock;
    public ReportService(ReservationRepository reservations, PaymentRepository payments, Clock clock) {
        this.reservations=reservations; this.payments=payments; this.clock=clock;
    }

    @Transactional(readOnly=true)
    public ReportSummary summarize(LocalDate from, LocalDate to) {
        if(from==null || to==null || to.isBefore(from)) throw new IllegalArgumentException("Choose a valid report date range.");
        if(ChronoUnit.DAYS.between(from,to)>366) throw new IllegalArgumentException("Report range cannot exceed 366 days.");
        var stays=reservations.findByCheckInDateBetweenOrderByCheckInDateAsc(from,to);
        ZoneId zone=clock.getZone();
        Instant start=from.atStartOfDay(zone).toInstant();
        Instant end=to.plusDays(1).atStartOfDay(zone).toInstant();
        BigDecimal revenue=payments.findByCreatedAtBetweenAndStatusOrderByCreatedAtAsc(start,end,PaymentStatus.PAID)
            .stream().map(Payment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        Map<String,Long> byRoom=stays.stream().collect(Collectors.groupingBy(
            r->r.getRoomCode()+" — "+r.getRoomName(),LinkedHashMap::new,Collectors.counting()));
        return new ReportSummary(from,to,stays.size(),
            stays.stream().filter(r->r.getStatus()==ReservationStatus.PENDING).count(),
            stays.stream().filter(r->r.getStatus()==ReservationStatus.APPROVED).count(),
            stays.stream().filter(r->r.getStatus()==ReservationStatus.REJECTED).count(),
            stays.stream().filter(r->r.getStatus()==ReservationStatus.CANCELLED).count(),
            revenue,byRoom);
    }
}