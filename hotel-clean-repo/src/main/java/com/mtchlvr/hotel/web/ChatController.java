package com.mtchlvr.hotel.web;

import com.mtchlvr.hotel.service.GroqChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

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

        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"))) {
            throw new AccessDeniedException("Manager chat is disabled");
        }

        List<Map<String, String>> history = request.history() == null ? List.of()
                : request.history().stream()
                    .filter(turn -> turn != null
                            && ("user".equals(turn.role()) || "assistant".equals(turn.role()))
                            && turn.content() != null && !turn.content().isBlank())
                    .skip(Math.max(0, request.history().size() - 10))
                    .map(turn -> Map.of("role", turn.role(), "content", turn.content().trim()))
                    .toList();

        return new ChatResponse(chatService.reply(request.message().trim(), history));
    }

    public record ChatRequest(
            @NotBlank @Size(max = 1000) String message,
            @Size(max = 10) List<@jakarta.validation.Valid ChatTurn> history) {}

    public record ChatTurn(
            @jakarta.validation.constraints.Pattern(regexp = "user|assistant") String role,
            @NotBlank @Size(max = 1000) String content) {}

    public record ChatResponse(String reply) {}
}
