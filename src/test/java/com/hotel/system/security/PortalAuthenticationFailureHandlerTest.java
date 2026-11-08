package com.hotel.system.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortalAuthenticationFailureHandlerTest {
    @Test
    void failureReturnsToRequestedPortal() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("portal", "admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new PortalAuthenticationFailureHandler().onAuthenticationFailure(
                request, response, new BadCredentialsException("invalid"));

        assertEquals("/admin/login?error", response.getRedirectedUrl());
    }

    @Test
    void invalidPortalCannotControlRedirect() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("portal", "//example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new PortalAuthenticationFailureHandler().onAuthenticationFailure(
                request, response, new BadCredentialsException("invalid"));

        assertEquals("/customer/login?error", response.getRedirectedUrl());
    }
}
