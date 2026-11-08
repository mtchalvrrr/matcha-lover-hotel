package com.hotel.system.room;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RoomController {
    private final RoomService rooms;

    public RoomController(RoomService rooms) {
        this.rooms = rooms;
    }

    @GetMapping("/rooms")
    public String browseRooms(Model model) {
        model.addAttribute("rooms", rooms.publicRooms());
        return "rooms/list";
    }

    @GetMapping("/rooms/{id}")
    public String roomDetails(@PathVariable Long id, Model model) {
        model.addAttribute("room", rooms.getPublicRoom(id));
        return "rooms/detail";
    }

    @GetMapping("/admin/rooms")
    public String manageRooms(Model model) {
        model.addAttribute("rooms", rooms.allRooms());
        return "admin/rooms/list";
    }

    @GetMapping("/admin/rooms/new")
    public String newRoom(Model model) {
        model.addAttribute("room", new Room());
        model.addAttribute("pageTitle", "Add room");
        return "admin/rooms/form";
    }

    @PostMapping("/admin/rooms")
    public String createRoom(@Valid @ModelAttribute("room") Room room,
                             BindingResult result, Model model,
                             RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("pageTitle", "Add room");
            return "admin/rooms/form";
        }
        try {
            rooms.create(room);
            redirect.addFlashAttribute("success", "Room created successfully.");
            return "redirect:/admin/rooms";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("pageTitle", "Add room");
            model.addAttribute("formError", ex.getMessage());
            return "admin/rooms/form";
        }
    }

    @GetMapping("/admin/rooms/{id}/edit")
    public String editRoom(@PathVariable Long id, Model model) {
        model.addAttribute("room", rooms.getRoom(id));
        model.addAttribute("pageTitle", "Edit room");
        return "admin/rooms/form";
    }

    @PostMapping("/admin/rooms/{id}")
    public String updateRoom(@PathVariable Long id, @Valid @ModelAttribute("room") Room room,
                             BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("pageTitle", "Edit room");
            return "admin/rooms/form";
        }
        try {
            rooms.update(id, room);
            redirect.addFlashAttribute("success", "Room updated successfully.");
            return "redirect:/admin/rooms";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("pageTitle", "Edit room");
            model.addAttribute("formError", ex.getMessage());
            return "admin/rooms/form";
        }
    }

    @PostMapping("/admin/rooms/{id}/status")
    public String changeRoomStatus(@PathVariable Long id, @RequestParam boolean active,
                                   RedirectAttributes redirect) {
        rooms.setActive(id, active);
        redirect.addFlashAttribute("success", active ? "Room activated." : "Room deactivated.");
        return "redirect:/admin/rooms";
    }
}