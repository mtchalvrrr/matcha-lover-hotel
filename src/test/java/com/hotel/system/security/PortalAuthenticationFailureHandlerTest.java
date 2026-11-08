package com.hotel.system.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PortalAuthenticationFailureHandlerTest {
    @Test
    void failureReturnsToRequestedPortalAndRecordsAttempt() throws Exception {
        LoginAttemptService attempts = mock(LoginAttemptService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("portal", "admin");
        request.setParameter("username", "booking-admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new PortalAuthenticationFailureHandler(attempts).onAuthenticationFailure(
                request, response, new BadCredentialsException("invalid"));

        assertEquals("/admin/login?error", response.getRedirectedUrl());
        verify(attempts).recordFailure("booking-admin");
    }

    @Test
    void invalidPortalCannotControlRedirect() throws Exception {
        LoginAttemptService attempts = mock(LoginAttemptService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("portal", "//example.com");
        request.setParameter("username", "intruder");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new PortalAuthenticationFailureHandler(attempts).onAuthenticationFailure(
                request, response, new BadCredentialsException("invalid"));

        assertEquals("/customer/login?error", response.getRedirectedUrl());
        verify(attempts).recordFailure("intruder");
    }
}
