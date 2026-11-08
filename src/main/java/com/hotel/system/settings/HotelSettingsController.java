package com.hotel.system.settings;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalTime;

@Controller
public class HotelSettingsController {
    private final HotelSettingsService settings;

    public HotelSettingsController(HotelSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/admin/settings")
    public String settings(Model model) {
        model.addAttribute("settings", settings.current());
        return "admin/settings";
    }

    @PostMapping("/admin/settings")
    public String update(@RequestParam String hotelName,
                         @RequestParam(required = false) String address,
                         @RequestParam(required = false) String contactInformation,
                         @RequestParam LocalTime checkInTime,
                         @RequestParam LocalTime checkOutTime,
                         @RequestParam(required = false) String cancellationPolicy,
                         Authentication authentication,
                         RedirectAttributes redirect) {
        try {
            settings.update(authentication.getName(), hotelName, address, contactInformation,
                    checkInTime, checkOutTime, cancellationPolicy);
            redirect.addFlashAttribute("success", "Hotel settings saved.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/settings";
    }
}
