package com.hotel.system.account;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordRecoveryController{
    private final PasswordRecoveryService recovery;
    public PasswordRecoveryController(PasswordRecoveryService recovery){this.recovery=recovery;}
    @GetMapping("/forgot-password")
    public String forgot(){return "auth/forgot-password";}
    @PostMapping("/forgot-password")
    public String request(@RequestParam String email,HttpServletRequest request,RedirectAttributes redirect){
        String base=request.getScheme()+"://"+request.getServerName()+(request.getServerPort()==80||request.getServerPort()==443?"":":"+request.getServerPort());
        try{recovery.request(email,base);}catch(IllegalStateException ignored){}
        redirect.addFlashAttribute("success","If an account uses that email, password recovery instructions will be sent.");
        return "redirect:/forgot-password";
    }
    @GetMapping("/reset-password")
    public String resetPage(@RequestParam String token,org.springframework.ui.Model model){
        model.addAttribute("token",token);return "auth/reset-password";
    }
    @PostMapping("/reset-password")
    public String reset(@RequestParam String token,@RequestParam String newPassword,RedirectAttributes redirect){
        try{recovery.reset(token,newPassword);redirect.addFlashAttribute("success","Password reset. You can now sign in.");return "redirect:/customer/login";}
        catch(IllegalArgumentException ex){redirect.addFlashAttribute("error",ex.getMessage());return "redirect:/reset-password?token="+token;}
    }
}