package com.hotel.system.notification;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class NotificationController{
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications){this.notifications=notifications;}

    @GetMapping({"/customer/notifications","/admin/notifications","/super-admin/notifications"})
    public String inbox(Authentication authentication,Model model,
                        @RequestParam String portal){
        model.addAttribute("notifications",notifications.inbox(authentication.getName()));
        model.addAttribute("portal",portal);
        return "notifications/inbox";
    }

    @PostMapping({"/customer/notifications/{id}/read","/admin/notifications/{id}/read","/super-admin/notifications/{id}/read"})
    public String markRead(@PathVariable Long id,Authentication authentication,
                           @RequestParam String portal,RedirectAttributes redirect){
        try{notifications.markRead(id,authentication.getName());}
        catch(IllegalArgumentException ex){redirect.addFlashAttribute("error",ex.getMessage());}
        return "redirect:/"+safePortal(portal)+"/notifications?portal="+safePortal(portal);
    }
    private String safePortal(String portal){
        return switch(portal){case "admin"->"admin";case "super-admin"->"super-admin";default->"customer";};
    }
}