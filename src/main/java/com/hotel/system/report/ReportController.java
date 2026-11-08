package com.hotel.system.report;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

@Controller
public class ReportController {
    private final ReportService reports;
    private final Clock clock;

    public ReportController(ReportService reports, Clock clock) {
        this.reports = reports;
        this.clock = clock;
    }

    @GetMapping("/admin/reports")
    public String reports(@RequestParam(required = false) LocalDate from,
                          @RequestParam(required = false) LocalDate to, Model model) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        model.addAttribute("summary", reports.summarize(start, end));
        return "admin/reports/index";
    }

    @GetMapping("/admin/reports/export.csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        ReportSummary summary = reports.summarize(from, to);
        StringBuilder csv = new StringBuilder("\uFEFF");
        append(csv, "Section", "Metric", "Value");
        append(csv, "Summary", "From date", summary.fromDate().toString());
        append(csv, "Summary", "To date", summary.toDate().toString());
        append(csv, "Summary", "Total reservations", summary.totalReservations());
        append(csv, "Summary", "Pending reservations", summary.pendingReservations());
        append(csv, "Summary", "Approved reservations", summary.approvedReservations());
        append(csv, "Summary", "Rejected reservations", summary.rejectedReservations());
        append(csv, "Summary", "Cancelled reservations", summary.cancelledReservations());
        append(csv, "Summary", "No-show reservations", summary.noShowReservations());
        append(csv, "Summary", "Checked-in reservations", summary.checkedInReservations());
        append(csv, "Summary", "Checked-out reservations", summary.checkedOutReservations());
        append(csv, "Summary", "Online reservations", summary.onlineReservations());
        append(csv, "Summary", "Walk-in reservations", summary.walkInReservations());
        append(csv, "Summary", "Unique customers", summary.uniqueCustomers());
        append(csv, "Summary", "New customers", summary.newCustomers());
        append(csv, "Summary", "Returning customers", summary.returningCustomers());
        append(csv, "Summary", "Reserved room nights", summary.reservedRoomNights());
        append(csv, "Summary", "Available room nights", summary.availableRoomNights());
        append(csv, "Summary", "Booked capacity percent", summary.bookedCapacityRate());
        append(csv, "Summary", "Paid revenue PHP", summary.paidRevenue());
        append(csv, "Summary", "Refunded amount PHP", summary.refundedAmount());
        append(csv, "Summary", "Outstanding balance PHP", summary.outstandingBalance());
        append(csv, "", "", "");
        append(csv, "Bookings by room", "Room", "Reservations");
        for (Map.Entry<String, Long> entry : summary.bookingsByRoom().entrySet()) {
            append(csv, "Bookings by room", entry.getKey(), entry.getValue());
        }
        append(csv, "", "", "");
        append(csv, "Daily reservation requests", "Creation date", "Reservations");
        for (Map.Entry<LocalDate, Long> entry : summary.bookingsByCreatedDate().entrySet()) {
            append(csv, "Daily reservation requests", entry.getKey(), entry.getValue());
        }
        append(csv, "", "", "");
        append(csv, "Daily check-in trend", "Date", "Reservations");
        for (Map.Entry<LocalDate, Long> entry : summary.bookingsByCheckInDate().entrySet()) {
            append(csv, "Daily check-in trend", entry.getKey(), entry.getValue());
        }
        append(csv, "", "", "");
        append(csv, "Daily paid revenue", "Payment date", "Revenue PHP");
        for (Map.Entry<LocalDate, BigDecimal> entry : summary.revenueByPaymentDate().entrySet()) {
            append(csv, "Daily paid revenue", entry.getKey(), entry.getValue());
        }

        String filename = "hotel-report-" + from + "-to-" + to + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void append(StringBuilder csv, Object section, Object metric, Object value) {
        csv.append(escape(section)).append(',')
                .append(escape(metric)).append(',')
                .append(escape(value)).append("\r\n");
    }

    private String escape(Object value) {
        String text = value == null ? "" : value.toString();
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
