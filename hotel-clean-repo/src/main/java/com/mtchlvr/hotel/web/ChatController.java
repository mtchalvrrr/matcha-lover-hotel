package com.mtchlvr.hotel.web;

import com.mtchlvr.hotel.service.GroqChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final GroqChatService chatService;

    public ChatController(GroqChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(
            @Valid @RequestBody ChatRequest request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException(
                    "Please sign in to use the hotel assistant.");
        }

        boolean manager = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"));

        String message = request.message().trim();
        String normalized = message.toLowerCase(Locale.ROOT);

        // Logout is available to both authenticated roles.
        if (containsAny(normalized, "logout", "log out", "sign out", "signout")) {
            return new ChatResponse(
                    "Would you like to log out of your mtchlvr Hotel account?",
                    List.of(new ChatAction("logout", "Confirm logout", "", true)));
        }

        // Manager-only functions are identified before any customer actions.
        String managerUrl = managerFunction(normalized);

        if (managerUrl != null) {
            if (!manager) {
                return new ChatResponse(
                        "Sorry! That function is available only to authorized hotel managers. "
                        + "I can help you browse rooms or manage your own guest account instead.",
                        List.of());
            }

            String label = switch (managerUrl) {
                case "/manager" -> "Open manager dashboard";
                case "/manager/reservation-requests" -> "Open reservation requests";
                case "/manager/room-management" -> "Open room management";
                case "/manager/history" -> "Open reservation history";
                case "/manager/reports" -> "Open guest reports";
                default -> "Open manager page";
            };

            return new ChatResponse(
                    "Certainly! You can open that manager function here:",
                    List.of(new ChatAction("navigate", label, managerUrl, false)));
        }

        // Customer-only functions must not send managers to protected customer pages.
        if (manager && containsAny(normalized,
                "my reservation", "my booking", "my bookings",
                "billing statement", "my bill", "my account", "customer profile",
                "send a report", "submit a report", "report a problem")) {
            return new ChatResponse(
                    "That is a customer account function. Your manager account has a separate management workspace.",
                    List.of());
        }

        if (!manager) {
            if (containsAny(normalized, "browse rooms", "available rooms",
                    "show rooms", "view rooms", "room list", "book a room",
                    "reserve a room", "make a reservation")) {
                return new ChatResponse(
                        "You can browse the hotel's rooms here. Actual date availability is checked when you submit a reservation.",
                        List.of(new ChatAction("navigate", "Browse rooms", "/rooms", false)));
            }

            if (containsAny(normalized, "my reservation", "my booking",
                    "my bookings", "reservation list", "booking status",
                    "view reservation", "view booking")) {
                return new ChatResponse(
                        "You can view your reservations, their status, and available actions here.",
                        List.of(new ChatAction("navigate", "My reservations", "/customer/reservations", false)));
            }

            if (containsAny(normalized, "billing statement", "my bill",
                    "billing", "room charge", "total cost", "payment statement")) {
                return new ChatResponse(
                        "Your reservation billing statements and room charges are displayed in the Billing statement column on your My Reservations page.",
                        List.of(new ChatAction("navigate", "View billing statements", "/customer/reservations", false)));
            }

            if (containsAny(normalized, "send a report", "submit a report",
                    "report a problem", "complaint", "contact the manager",
                    "guest report")) {
                return new ChatResponse(
                        "You can submit a report and view your conversations with the hotel from My Reservations. Open the Send a report control there.",
                        List.of(new ChatAction("navigate", "Open My Reservations", "/customer/reservations", false)));
            }

            if (containsAny(normalized, "cancel reservation", "cancel booking",
                    "cancel my booking", "cancel my reservation")) {
                return new ChatResponse(
                        "You can request cancellation from My Reservations. Select the reservation and use its Request cancellation button. The request is sent to the manager for review; I cannot cancel it for you.",
                        List.of(new ChatAction("navigate", "View my reservations", "/customer/reservations", false)));
            }

            if (containsAny(normalized, "rating", "ratings", "review",
                    "reviews", "rate my stay")) {
                return new ChatResponse(
                        "I couldn't find an implemented customer ratings or reviews page in the current application. I don't want to send you to a page that doesn't exist.",
                        List.of());
            }
        }

        // Ordinary hotel questions continue through the existing Groq integration.
        return new ChatResponse(chatService.reply(message), List.of());
    }

    private static String managerFunction(String message) {
        if (containsAny(message, "manager dashboard", "management dashboard",
                "manager home", "open dashboard")) {
            return "/manager";
        }

        if (containsAny(message, "reservation requests", "booking requests",
                "approve reservation", "approve booking", "reject reservation",
                "reject booking", "pending requests", "requests to review")) {
            return "/manager/reservation-requests";
        }

        if (containsAny(message, "room management", "manage rooms",
                "edit rooms", "deactivate room", "reactivate room")) {
            return "/manager/room-management";
        }

        if (containsAny(message, "reservation history", "booking history",
                "past reservations", "past bookings", "manager history")) {
            return "/manager/history";
        }

        if (containsAny(message, "all guest reports", "guest reports",
                "manager reports", "manage reports", "review reports")) {
            return "/manager/reports";
        }

        return null;
    }

    private static boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) return true;
        }
        return false;
    }

    public record ChatRequest(
            @NotBlank @Size(max = 1000) String message) {}

    public record ChatAction(
            String id, String label, String url, boolean confirmationRequired) {}

    public record ChatResponse(String reply, List<ChatAction> actions) {}
}
