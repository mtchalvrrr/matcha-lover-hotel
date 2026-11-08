package com.hotel.system.account;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordRecoveryControllerTest {
    @Test
    void usesConfiguredPublicUrlInsteadOfRequestHostHeader() {
        PasswordRecoveryService recovery = mock(PasswordRecoveryService.class);
        PasswordRecoveryController controller = new PasswordRecoveryController(
                recovery, "https://hotel.example.com/");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.request("guest@example.com", redirect);

        assertEquals("redirect:/forgot-password", view);
        verify(recovery).request("guest@example.com", "https://hotel.example.com");
        assertTrue(redirect.getFlashAttributes().containsKey("success"));
    }

    @Test
    void rejectsPasswordResetUrlsWithCredentialsOrNonHttpSchemes() {
        PasswordRecoveryService recovery = mock(PasswordRecoveryService.class);

        assertThrows(IllegalArgumentException.class,
                () -> new PasswordRecoveryController(recovery, "https://user:pass@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> new PasswordRecoveryController(recovery, "javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class,
                () -> new PasswordRecoveryController(recovery, "https://example.com?next=evil"));
    }
}
