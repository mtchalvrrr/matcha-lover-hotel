package com.hotel.system.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PortalController {
    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/customer")
    public String customerProgram() {
        return "customer-program";
    }

    @GetMapping("/admin")
    public String adminProgram() {
        return "admin-program";
    }

    @GetMapping("/super-admin")
    public String superAdminProgram() {
        return "super-admin-program";
    }

    @GetMapping("/customer/login")
    public String customerLogin() {
        return "auth/customer-login";
    }

    @GetMapping("/admin/login")
    public String adminLogin() {
        return "auth/admin-login";
    }

    @GetMapping("/super-admin/login")
    public String superAdminLogin() {
        return "auth/super-admin-login";
    }

    @GetMapping("/customer/dashboard")
    public String customerDashboard() {
        return "dashboards/customer-dashboard";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard() {
        return "dashboards/admin-dashboard";
    }

    @GetMapping("/super-admin/dashboard")
    public String superAdminDashboard() {
        return "dashboards/super-admin-dashboard";
    }
}
