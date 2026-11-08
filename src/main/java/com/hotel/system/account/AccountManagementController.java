package com.hotel.system.account;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountManagementController {
    private final AccountManagementService accounts;
    public AccountManagementController(AccountManagementService accounts) { this.accounts = accounts; }

    @GetMapping("/super-admin/accounts")
    public String accounts(Model model) {
        model.addAttribute("accounts", accounts.manageableAccounts());
        return "super-admin/accounts/list";
    }

    @GetMapping("/super-admin/accounts/new")
    public String newAdmin() { return "super-admin/accounts/form"; }

    @PostMapping("/super-admin/accounts")
    public String createAdmin(@RequestParam String username, @RequestParam String email,
                              @RequestParam String password, Authentication authentication,
                              RedirectAttributes redirect) {
        try {
            accounts.createAdmin(authentication.getName(), username, email, password);
            redirect.addFlashAttribute("success", "Admin account created.");
            return "redirect:/super-admin/accounts";
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/super-admin/accounts/new";
        }
    }

    @PostMapping("/super-admin/accounts/{id}/status")
    public String changeStatus(@PathVariable Long id, @RequestParam boolean active,
                               Authentication authentication, RedirectAttributes redirect) {
        try {
            accounts.setAccountActive(authentication.getName(), id, active);
            redirect.addFlashAttribute("success", active ? "Account reactivated." : "Account suspended.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/super-admin/accounts";
    }
}