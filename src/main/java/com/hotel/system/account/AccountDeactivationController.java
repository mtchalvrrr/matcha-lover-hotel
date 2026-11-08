package com.hotel.system.account;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountDeactivationController {
    private final AccountDeactivationService deactivations;

    public AccountDeactivationController(AccountDeactivationService deactivations) {
        this.deactivations = deactivations;
    }

    @GetMapping("/customer/account-deactivation")
    public String customerPage(Authentication authentication, Model model) {
        model.addAttribute("requests", deactivations.customerRequests(authentication.getName()));
        return "customer/account-deactivation";
    }

    @PostMapping("/customer/account-deactivation")
    public String request(@RequestParam String reason, Authentication authentication,
                          RedirectAttributes redirect) {
        try {
            deactivations.request(authentication.getName(), reason);
            redirect.addFlashAttribute("success", "Your deactivation request was sent for review.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/customer/account-deactivation";
    }

    @GetMapping("/super-admin/deactivations")
    public String pending(Model model) {
        model.addAttribute("requests", deactivations.pendingRequests());
        return "super-admin/deactivations/list";
    }

    @PostMapping("/super-admin/deactivations/{id}/review")
    public String review(@PathVariable Long id, @RequestParam boolean approve,
                         @RequestParam(required = false) String reason,
                         Authentication authentication, RedirectAttributes redirect) {
        try {
            deactivations.review(id, approve, authentication.getName(), reason);
            redirect.addFlashAttribute("success", approve
                    ? "Customer account deactivated."
                    : "Deactivation request rejected.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/super-admin/deactivations";
    }
}
