package com.hotel.system.room;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;
import com.hotel.system.config.HotelStayTimePolicy;
import org.springframework.security.core.GrantedAuthority;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RoomController {
    private final RoomService rooms;
    private final Clock clock;
    private final RoomPhotoService photos;
    private final HotelStayTimePolicy stayTimes;

    public RoomController(RoomService rooms, Clock clock, RoomPhotoService photos, HotelStayTimePolicy stayTimes) {
        this.rooms = rooms;
        this.clock = clock;
        this.photos = photos;
        this.stayTimes = stayTimes;
    }

    @GetMapping("/rooms")
    public String browseRooms(@RequestParam(required = false) LocalDate checkIn,
                              @RequestParam(required = false) LocalDate checkOut,
                              @RequestParam(required = false) Integer guests,
                              Model model) {
        var availableRooms = rooms.publicRooms();
        boolean availabilitySearch = checkIn != null || checkOut != null || guests != null;
        try {
            if (availabilitySearch) {
                availableRooms = rooms.searchAvailable(checkIn, checkOut, guests == null ? 1 : guests);
            }
        } catch (IllegalArgumentException ex) {
            model.addAttribute("availabilityError", ex.getMessage());
            availableRooms = rooms.publicRooms();
            availabilitySearch = false;
        }
        model.addAttribute("rooms", availableRooms);
        model.addAttribute("roomTypes", rooms.publicRooms().stream()
                .map(Room::getRoomType).filter(java.util.Objects::nonNull)
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList());
        model.addAttribute("availabilitySearch", availabilitySearch && checkIn != null && checkOut != null);
        model.addAttribute("checkIn", checkIn);
        model.addAttribute("checkOut", checkOut);
        model.addAttribute("guestCount", guests == null ? 1 : guests);
        model.addAttribute("minimumDate", LocalDate.now(clock));
        return "rooms/list";
    }

    @GetMapping("/rooms/{id}")
    public String roomDetails(@PathVariable Long id,
                              @RequestParam(required = false) LocalDate checkIn,
                              @RequestParam(required = false) LocalDate checkOut,
                              @RequestParam(required = false) Integer guests,
                              Model model, Authentication authentication) {
        model.addAttribute("room", rooms.getPublicRoom(id));
        model.addAttribute("photos", photos.list(id));
        boolean customerSignedIn = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).anyMatch("ROLE_CUSTOMER"::equals);
        model.addAttribute("customerSignedIn", customerSignedIn);
        model.addAttribute("minimumDate", LocalDate.now(clock));
        model.addAttribute("checkIn", checkIn);
        model.addAttribute("checkOut", checkOut);
        model.addAttribute("guestCount", guests == null ? 1 : guests);
        model.addAttribute("availabilityChecked", checkIn != null && checkOut != null);
        model.addAttribute("availabilityAvailable", checkIn != null && checkOut != null
                && (guests == null || (guests >= 1 && guests <= rooms.getPublicRoom(id).getCapacity()))
                && rooms.isAvailable(id, checkIn, checkOut));
        model.addAttribute("checkInTime", stayTimes.checkInTime());
        model.addAttribute("checkOutTime", stayTimes.checkOutTime());
        return "rooms/detail";
    }

    @GetMapping("/admin/rooms")
    public String manageRooms(Model model) {
        model.addAttribute("rooms", rooms.allRooms());
        model.addAttribute("cleaningStatuses", CleaningStatus.values());
        model.addAttribute("maintenanceStatuses", MaintenanceStatus.values());
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
                             BindingResult result, Model model, Authentication authentication,
                             RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("pageTitle", "Add room");
            return "admin/rooms/form";
        }
        try {
            rooms.create(room, authentication.getName());
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
                             BindingResult result, Model model, Authentication authentication, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("pageTitle", "Edit room");
            return "admin/rooms/form";
        }
        try {
            rooms.update(id, room, authentication.getName());
            redirect.addFlashAttribute("success", "Room updated successfully.");
            return "redirect:/admin/rooms";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("pageTitle", "Edit room");
            model.addAttribute("formError", ex.getMessage());
            return "admin/rooms/form";
        }
    }

    @PostMapping("/admin/rooms/{id}/operations")
    public String updateRoomOperations(@PathVariable Long id,
                                       @RequestParam CleaningStatus cleaningStatus,
                                       @RequestParam MaintenanceStatus maintenanceStatus,
                                       Authentication authentication,
                                       RedirectAttributes redirect) {
        try {
            rooms.updateOperationalStatus(id, cleaningStatus, maintenanceStatus, authentication.getName());
            redirect.addFlashAttribute("success", "Room housekeeping and maintenance status updated.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/rooms";
    }

    @PostMapping("/admin/rooms/{id}/status")
    public String changeRoomStatus(@PathVariable Long id, @RequestParam boolean active,
                                   Authentication authentication, RedirectAttributes redirect) {
        rooms.setActive(id, active, authentication.getName());
        redirect.addFlashAttribute("success", active ? "Room activated." : "Room deactivated.");
        return "redirect:/admin/rooms";
    }
}