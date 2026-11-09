package com.hotel.system.support;

import com.hotel.system.settings.HotelSettingsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SupportController {
    private final SupportService support;
    private final HotelSettingsService hotelSettings;

    public SupportController(SupportService support, HotelSettingsService hotelSettings) {
        this.support = support;
        this.hotelSettings = hotelSettings;
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/customer/support")
    public String customerSupport(Authentication authentication, Model model) {
        model.addAttribute("hotelSettings", hotelSettings.current());
        model.addAttribute("threads", support.customerThreads(authentication.getName()));
        return "support/customer";
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/customer/support")
    public String createSupport(@RequestParam String category, @RequestParam String subject,
                                @RequestParam String message, Authentication authentication,
                                RedirectAttributes redirect) {
        try {
            Long id = support.createCase(authentication.getName(), category, subject, message);
            redirect.addFlashAttribute("success", "Your message has been sent to the manager.");
            return "redirect:/customer/support#case-" + id;
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/customer/support";
        }
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/customer/support/{id}/reply")
    public String customerReply(@PathVariable Long id, @RequestParam String message,
                                Authentication authentication, RedirectAttributes redirect) {
        try {
            support.customerReply(id, authentication.getName(), message);
            redirect.addFlashAttribute("success", "Your reply has been sent.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/customer/support#case-" + id;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/support")
    public String managerSupport(Model model) {
        model.addAttribute("hotelSettings", hotelSettings.current());
        model.addAttribute("threads", support.managerThreads());
        model.addAttribute("statuses", SupportStatus.values());
        return "support/manager";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/support/{id}/reply")
    public String managerReply(@PathVariable Long id, @RequestParam String message,
                               Authentication authentication, RedirectAttributes redirect) {
        try {
            support.managerReply(id, authentication.getName(), message);
            redirect.addFlashAttribute("success", "Reply sent to the customer.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/support#case-" + id;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/support/{id}/status")
    public String updateStatus(@PathVariable Long id, @RequestParam SupportStatus status,
                               Authentication authentication, RedirectAttributes redirect) {
        try {
            support.updateStatus(id, authentication.getName(), status);
            redirect.addFlashAttribute("success", "Support request status updated.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/support#case-" + id;
    }
}
