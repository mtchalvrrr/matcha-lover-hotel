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
}
