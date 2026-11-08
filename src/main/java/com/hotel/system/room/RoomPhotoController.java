package com.hotel.system.room;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RoomPhotoController {
    private final RoomService rooms;
    private final RoomPhotoService photos;
    public RoomPhotoController(RoomService rooms, RoomPhotoService photos){this.rooms=rooms;this.photos=photos;}

    @GetMapping("/admin/rooms/{id}/photos")
    public String manage(@PathVariable Long id, Model model){
        model.addAttribute("room",rooms.getRoom(id));
        model.addAttribute("photos",photos.list(id));
        return "admin/rooms/photos";
    }
    @PostMapping("/admin/rooms/{id}/photos")
    public String upload(@PathVariable Long id,@RequestParam MultipartFile photo,Authentication auth,RedirectAttributes redirect){
        try{photos.upload(id,photo,auth.getName());redirect.addFlashAttribute("success","Photo uploaded.");}
        catch(IllegalArgumentException|IllegalStateException ex){redirect.addFlashAttribute("error",ex.getMessage());}
        return "redirect:/admin/rooms/"+id+"/photos";
    }
    @PostMapping("/admin/rooms/{id}/photos/{photoId}/cover")
    public String cover(@PathVariable Long id,@PathVariable Long photoId,Authentication auth,RedirectAttributes redirect){
        try{photos.setCover(id,photoId,auth.getName());redirect.addFlashAttribute("success","Cover photo updated.");}
        catch(IllegalArgumentException ex){redirect.addFlashAttribute("error",ex.getMessage());}
        return "redirect:/admin/rooms/"+id+"/photos";
    }
    @PostMapping("/admin/rooms/{id}/photos/{photoId}/delete")
    public String delete(@PathVariable Long id,@PathVariable Long photoId,Authentication auth,RedirectAttributes redirect){
        try{photos.delete(id,photoId,auth.getName());redirect.addFlashAttribute("success","Photo removed.");}
        catch(IllegalArgumentException|IllegalStateException ex){redirect.addFlashAttribute("error",ex.getMessage());}
        return "redirect:/admin/rooms/"+id+"/photos";
    }
}