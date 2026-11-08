package com.hotel.system.chat;

import com.hotel.system.config.HotelStayTimePolicy;
import com.hotel.system.settings.HotelSettings;
import com.hotel.system.settings.HotelSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatAssistantServiceTest {
    private final HotelSettingsService settings = mock(HotelSettingsService.class);
    private final HotelStayTimePolicy stayTimes = mock(HotelStayTimePolicy.class);

    private ChatAssistantService service() {
        when(settings.current()).thenReturn(new HotelSettings(
                "[HOTEL NAME]", "[HOTEL ADDRESS]", "[HOTEL CONTACT INFORMATION]",
                LocalTime.of(14, 0), LocalTime.NOON, "Contact the hotel for cancellations."));
        when(stayTimes.checkInTime()).thenReturn(LocalTime.of(14, 0));
        when(stayTimes.checkOutTime()).thenReturn(LocalTime.NOON);
        return new ChatAssistantService(RestClient.builder(), false, "",
                "https://api.example.invalid/chat", "test-model", settings, stayTimes);
    }

    @Test
    void roomIntentRedirectsToPublicRoomCatalog() {
        ChatAssistantService.ChatReply reply = service().reply("Show me the rooms", false);

        assertEquals("/rooms", reply.redirectUrl());
        assertTrue(reply.reply().contains("browse"));
    }

    @Test
    void signedOutPrivateReservationQuestionRedirectsToCustomerLogin() {
        ChatAssistantService.ChatReply reply = service().reply("Can I see my reservation?", false);

        assertEquals("/customer/login", reply.redirectUrl());
        assertTrue(reply.reply().contains("sign in"));
    }

    @Test
    void signedInPaymentQuestionRedirectsToPaymentHistory() {
        ChatAssistantService.ChatReply reply = service().reply("Show my payment history", true);

        assertEquals("/customer/payments", reply.redirectUrl());
    }

    @Test
    void signedInReservationQuestionRedirectsToReservations() {
        ChatAssistantService.ChatReply reply = service().reply("Show my reservation", true);

        assertEquals("/customer/reservations", reply.redirectUrl());
    }

    @Test
    void answersCheckInAndCheckOutFaqWithoutExternalAi() {
        ChatAssistantService.ChatReply reply = service().reply("What time is check-in and check-out?", false);

        assertNull(reply.redirectUrl());
        assertTrue(reply.reply().contains("14:00"));
        assertTrue(reply.reply().contains("12:00"));
    }

    @Test
    void blocksSensitiveCredentialsAndEmailFromAiProcessing() {
        ChatAssistantService assistant = service();

        assertTrue(assistant.reply("My password is Secret123!", false).reply().contains("do not send passwords"));
        assertTrue(assistant.reply("My email is guest@example.com", false).reply().contains("secure account pages"));
        assertTrue(assistant.reply("My card is 4111 1111 1111 1111", false).reply().contains("payment-card numbers"));
    }

    @Test
    void rejectsBlankAndOverlongMessages() {
        ChatAssistantService assistant = service();
        assertThrows(IllegalArgumentException.class, () -> assistant.reply(" ", false));
        assertThrows(IllegalArgumentException.class, () -> assistant.reply("x".repeat(1001), false));
    }
}
