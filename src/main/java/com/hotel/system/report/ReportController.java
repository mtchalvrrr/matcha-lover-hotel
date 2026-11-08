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
import java.util.ArrayList;
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

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
        ReportSummary summary;
        try {
            summary = reports.summarize(start, end);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("reportError", ex.getMessage());
            end = LocalDate.now(clock);
            start = end.minusDays(29);
            summary = reports.summarize(start, end);
        }
        model.addAttribute("summary", summary);
        model.addAttribute("dailyRequestTotal", summary.bookingsByCreatedDate().values().stream()
                .mapToLong(Long::longValue).sum());
        return "admin/reports/index";
    }

    @GetMapping("/admin/reports/export.csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        ReportSummary summary;
        try {
            summary = reports.summarize(from, to);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(ex.getMessage().getBytes(StandardCharsets.UTF_8));
        }
        StringBuilder csv = new StringBuilder("\uFEFF");
        append(csv, "Section", "Metric", "Value");
        append(csv, "Summary", "From date", summary.fromDate().toString());
        append(csv, "Summary", "To date", summary.toDate().toString());
        append(csv, "Summary", "Total reservations", summary.totalReservations());
        append(csv, "Summary", "Pending reservations", summary.pendingReservations());
        append(csv, "Summary", "Approved reservations", summary.approvedReservations());
        append(csv, "Summary", "Rejected reservations", summary.rejectedReservations());
        append(csv, "Summary", "Cancelled reservations", summary.cancelledReservations());
        append(csv, "Summary", "Expired reservations", summary.expiredReservations());
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
        append(csv, "Summary", "Gross payments received PHP", summary.paidRevenue());
        append(csv, "Summary", "Refunded amount PHP", summary.refundedAmount());
        append(csv, "Summary", "Outstanding balance PHP", summary.outstandingBalance());
        append(csv, "", "", "");
        append(csv, "Reservations by room", "Room", "Reservations");
        for (Map.Entry<String, Long> entry : summary.bookingsByRoom().entrySet()) {
            append(csv, "Bookings by room", entry.getKey(), entry.getValue());
        }
        append(csv, "", "", "");
        append(csv, "Daily reservation requests", "Creation date", "Reservations");
        for (Map.Entry<LocalDate, Long> entry : summary.bookingsByCreatedDate().entrySet()) {
            append(csv, "Daily reservation requests", entry.getKey(), entry.getValue());
        }
        append(csv, "", "", "");
        append(csv, "Reservations by check-in date", "Date", "Reservations");
        for (Map.Entry<LocalDate, Long> entry : summary.bookingsByCheckInDate().entrySet()) {
            append(csv, "Daily check-in trend", entry.getKey(), entry.getValue());
        }
        append(csv, "", "", "");
        append(csv, "Daily gross payments received", "Payment date", "Revenue PHP");
        for (Map.Entry<LocalDate, BigDecimal> entry : summary.revenueByPaymentDate().entrySet()) {
            append(csv, "Daily paid revenue", entry.getKey(), entry.getValue());
        }

        String filename = "hotel-report-" + from + "-to-" + to + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/admin/reports/export.pdf")
    public ResponseEntity<byte[]> exportPdf(@RequestParam LocalDate from,
                                            @RequestParam LocalDate to) throws IOException {
        ReportSummary summary;
        try {
            summary = reports.summarize(from, to);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN)
                    .body(ex.getMessage().getBytes(StandardCharsets.UTF_8));
        }

        List<String> lines = new ArrayList<>();
        lines.add("[HOTEL NAME] — HOTEL PERFORMANCE REPORT");
        lines.add("Reporting period: " + summary.fromDate() + " to " + summary.toDate());
        lines.add("");
        lines.add("RESERVATIONS");
        lines.add("Total: " + summary.totalReservations()
                + " | Pending: " + summary.pendingReservations()
                + " | Approved: " + summary.approvedReservations());
        lines.add("Rejected: " + summary.rejectedReservations()
                + " | Cancelled: " + summary.cancelledReservations()
                + " | Expired: " + summary.expiredReservations());
        lines.add("No-show: " + summary.noShowReservations()
                + " | Checked in: " + summary.checkedInReservations()
                + " | Checked out: " + summary.checkedOutReservations());
        lines.add("Online: " + summary.onlineReservations()
                + " | Walk-in: " + summary.walkInReservations());
        lines.add("");
        lines.add("OCCUPANCY AND CUSTOMERS");
        lines.add("Reserved room nights: " + summary.reservedRoomNights()
                + " / " + summary.availableRoomNights()
                + " (" + summary.bookedCapacityRate() + "%)");
        lines.add("Unique customers: " + summary.uniqueCustomers()
                + " | New: " + summary.newCustomers()
                + " | Returning: " + summary.returningCustomers());
        lines.add("");
        lines.add("PAYMENTS (PHP)");
        lines.add("Gross payments received: " + summary.paidRevenue());
        lines.add("Refunded: " + summary.refundedAmount());
        lines.add("Outstanding balance: " + summary.outstandingBalance());
        lines.add("");
        lines.add("RESERVATIONS BY ROOM");
        summary.bookingsByRoom().forEach((room, count) -> lines.add(room + ": " + count));
        lines.add("");
        lines.add("DAILY RESERVATION REQUESTS");
        summary.bookingsByCreatedDate().forEach((date, count) -> lines.add(date + ": " + count));
        lines.add("");
        lines.add("RESERVATIONS BY CHECK-IN DATE");
        summary.bookingsByCheckInDate().forEach((date, count) -> lines.add(date + ": " + count));
        lines.add("");
        lines.add("GROSS PAYMENTS BY PAYMENT DATE (PHP)");
        summary.revenueByPaymentDate().forEach((date, amount) -> lines.add(date + ": " + amount));

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(document, page);
            float y = 790;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (y < 55) {
                    stream.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    stream = new PDPageContentStream(document, page);
                    y = 790;
                }
                stream.beginText();
                stream.setFont(i == 0 || line.matches("^[A-Z][A-Z ()/]+$") ? bold : regular,
                        i == 0 ? 14 : 10);
                stream.newLineAtOffset(48, y);
                String safeLine = line.replaceAll("[^\\x20-\\x7E]", "?");
                if (safeLine.length() > 105) safeLine = safeLine.substring(0, 102) + "...";
                stream.showText(safeLine);
                stream.endText();
                y -= (line.isBlank() ? 8 : 16);
            }
            stream.close();
            document.save(output);
            byte[] pdf = output.toByteArray();
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=hotel-report-" + from + "-to-" + to + ".pdf")
                    .body(pdf);
        }
    }

    private void append(StringBuilder csv, Object section, Object metric, Object value) {
        csv.append(escape(section)).append(',')
                .append(escape(metric)).append(',')
                .append(escape(value)).append("\r\n");
    }

    private boolean startsWithSpreadsheetFormula(String value) {
        int index = 0;
        while (index < value.length()
                && (Character.isWhitespace(value.charAt(index))
                    || Character.isISOControl(value.charAt(index)))) {
            index++;
        }
        return index < value.length() && "=+-@".indexOf(value.charAt(index)) >= 0;
    }

    private String escape(Object value) {
        String text = value == null ? "" : value.toString();
        if (startsWithSpreadsheetFormula(text)) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
