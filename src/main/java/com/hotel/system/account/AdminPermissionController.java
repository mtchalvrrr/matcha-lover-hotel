package com.hotel.system.account;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminPermissionController {
    private final AdminPermissionService permissions;
    public AdminPermissionController(AdminPermissionService permissions) { this.permissions = permissions; }

    @GetMapping("/super-admin/permissions")
    public String permissions(Model model) {
        model.addAttribute("permissions", permissions.all());
        return "super-admin/permissions";
    }

    @PostMapping("/super-admin/permissions/{key}")
    public String update(@PathVariable String key,
                        @RequestParam(defaultValue = "false") boolean enabled,
                        Authentication authentication, RedirectAttributes redirect) {
        try {
            permissions.update(key, enabled, authentication.getName());
            redirect.addFlashAttribute("success", "Admin permission updated. Both Admin accounts receive the same setting.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/super-admin/permissions";
    }
}
