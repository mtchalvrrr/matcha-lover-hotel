package com.hotel.system.web;

import com.hotel.system.dashboard.DashboardService;
import com.hotel.system.reservation.Reservation;
import com.hotel.system.reservation.ReservationService;
import com.hotel.system.reservation.ReservationStatus;
import com.hotel.system.settings.HotelSettingsService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PortalController {
    private final DashboardService dashboards;
    private final ReservationService reservations;
    private final HotelSettingsService hotelSettings;

    public PortalController(DashboardService dashboards, ReservationService reservations, HotelSettingsService hotelSettings) {
        this.dashboards = dashboards;
        this.reservations = reservations;
        this.hotelSettings = hotelSettings;
    }

    @GetMapping("/")
    public String home() { return "index"; }

    @GetMapping("/customer")
    public String customerProgram(Model model) {
        model.addAttribute("hotelSettings", hotelSettings.current());
        return "customer-program";
    }

    @GetMapping("/admin")
    public String adminProgram() { return "admin-program"; }

    @GetMapping("/super-admin")
    public String superAdminProgram() { return "super-admin-program"; }

    @GetMapping("/customer/login")
    public String customerLogin() { return "auth/customer-login"; }

    @GetMapping("/admin/login")
    public String adminLogin() { return "auth/admin-login"; }

    @GetMapping("/super-admin/login")
    public String superAdminLogin() { return "auth/super-admin-login"; }

    @GetMapping("/customer/dashboard")
    public String customerDashboard(Authentication authentication, Model model) {
        var customerReservations = reservations.customerReservations(authentication.getName());
        model.addAttribute("hasReservations", !customerReservations.isEmpty());
        model.addAttribute("approvedStays", customerReservations.stream()
                .filter(reservation -> reservation.getStatus() == ReservationStatus.APPROVED
                        || reservation.getStatus() == ReservationStatus.CHECKED_IN)
                .toList());
        return "dashboards/customer-dashboard";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model) {
        model.addAttribute("summary", dashboards.adminSummary());
        return "dashboards/admin-dashboard";
    }

    @GetMapping("/super-admin/dashboard")
    public String superAdminDashboard(Model model) {
        model.addAttribute("summary", dashboards.superAdminSummary());
        return "dashboards/super-admin-dashboard";
    }
}
