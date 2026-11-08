package com.hotel.system.report;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
import java.time.LocalDate;

@Controller
public class ReportController {
    private final ReportService reports;
    private final Clock clock;
    public ReportController(ReportService reports, Clock clock){this.reports=reports;this.clock=clock;}

    @GetMapping("/admin/reports")
    public String reports(@RequestParam(required=false) LocalDate from,
                          @RequestParam(required=false) LocalDate to, Model model) {
        LocalDate end=to==null?LocalDate.now(clock):to;
        LocalDate start=from==null?end.minusDays(29):from;
        model.addAttribute("summary",reports.summarize(start,end));
        return "admin/reports/index";
    }
}