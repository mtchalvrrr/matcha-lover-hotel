package com.hotel.system.room;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RoomRatingController {
    private final RoomRatingService ratings;

    public RoomRatingController(RoomRatingService ratings) {
        this.ratings = ratings;
    }

    @PostMapping("/customer/reservations/{id}/rating")
    public String submitRating(@PathVariable Long id, @RequestParam int stars,
                               @RequestParam(required = false) String reviewText,
                               Authentication authentication, RedirectAttributes redirect) {
        try {
            ratings.rateCompletedStay(id, authentication.getName(), stars, reviewText);
            redirect.addFlashAttribute("success", "Thank you. Your rating and feedback have been saved.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/customer/reservations";
    }
}
