package com.hotel.system.chat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

@Controller
public class ChatAssistantController {
    private static final String RATE_LIMIT_SESSION_KEY = "hotelChatRequestTimes";
    private static final int MAX_REQUESTS_PER_MINUTE = 12;

    private final ChatAssistantService assistant;
    private final Clock clock;

    public ChatAssistantController(ChatAssistantService assistant, Clock clock) {
        this.assistant = assistant;
        this.clock = clock;
    }

    @PostMapping("/api/chat")
    @ResponseBody
    public ResponseEntity<ChatAssistantService.ChatReply> chat(
            @Valid @RequestBody ChatMessageRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        if (!withinRateLimit(servletRequest.getSession(true))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                    new ChatAssistantService.ChatReply("Please wait a moment before sending another message.", null));
        }
        try {
            boolean signedInCustomer = authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_CUSTOMER".equals(authority.getAuthority()));
            return ResponseEntity.ok(assistant.reply(request.message(), signedInCustomer));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new ChatAssistantService.ChatReply(ex.getMessage(), null));
        }
    }

    private boolean withinRateLimit(HttpSession session) {
        @SuppressWarnings("unchecked")
        Deque<Instant> requestTimes = (Deque<Instant>) session.getAttribute(RATE_LIMIT_SESSION_KEY);
        if (requestTimes == null) {
            requestTimes = new ArrayDeque<>();
            session.setAttribute(RATE_LIMIT_SESSION_KEY, requestTimes);
        }
        synchronized (requestTimes) {
            Instant now = Instant.now(clock);
            Instant cutoff = now.minusSeconds(60);
            while (!requestTimes.isEmpty() && requestTimes.peekFirst().isBefore(cutoff)) {
                requestTimes.removeFirst();
            }
            if (requestTimes.size() >= MAX_REQUESTS_PER_MINUTE) return false;
            requestTimes.addLast(now);
            return true;
        }
    }

    public record ChatMessageRequest(@NotBlank @Size(max = 1000) String message) { }
}
