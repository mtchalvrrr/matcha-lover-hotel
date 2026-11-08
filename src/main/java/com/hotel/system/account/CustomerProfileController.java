package com.hotel.system.account;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CustomerProfileController {
    private final CustomerProfileService profiles;
    public CustomerProfileController(CustomerProfileService profiles){this.profiles=profiles;}

    @GetMapping("/customer/profile")
    public String profile(Authentication authentication,Model model){
        model.addAttribute("account",profiles.profile(authentication.getName()));
        return "customer/profile";
    }
    @PostMapping("/customer/profile")
    public String update(@RequestParam String firstName,@RequestParam String lastName,
        @RequestParam String email,Authentication authentication,RedirectAttributes redirect){
        try{profiles.update(authentication.getName(),firstName,lastName,email);redirect.addFlashAttribute("success","Profile updated.");}
        catch(IllegalArgumentException ex){redirect.addFlashAttribute("error",ex.getMessage());}
        return "redirect:/customer/profile";
    }
    @PostMapping("/customer/profile/password")
    public String password(@RequestParam String currentPassword,@RequestParam String newPassword,
        Authentication authentication,RedirectAttributes redirect){
        try{profiles.changePassword(authentication.getName(),currentPassword,newPassword);redirect.addFlashAttribute("success","Password changed.");}
        catch(IllegalArgumentException ex){redirect.addFlashAttribute("error",ex.getMessage());}
        return "redirect:/customer/profile";
    }
}