package com.hotel.system.security;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortalAuthenticationSuccessHandlerTest {
    private final PortalAuthenticationSuccessHandler handler = new PortalAuthenticationSuccessHandler();

    @Test
    void customerRoleCanEnterCustomerPortal() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("portal", "customer");
        MockHttpServletResponse response = new MockHttpServletResponse();
        var authentication = new TestingAuthenticationToken("guest", "hash", "ROLE_CUSTOMER");

        handler.onAuthenticationSuccess(request, response, authentication);

        assertEquals("/customer/dashboard", response.getRedirectedUrl());
    }

    @Test
    void adminRoleCannotEnterSuperAdminPortal() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("portal", "super-admin");
        HttpSession session = request.getSession();
        MockHttpServletResponse response = new MockHttpServletResponse();
        var authentication = new TestingAuthenticationToken("booking-admin", "hash", "ROLE_ADMIN");

        handler.onAuthenticationSuccess(request, response, authentication);

        assertEquals("/super-admin/login?denied", response.getRedirectedUrl());
    }

    @Test
    void adminRoleCanEnterAdminPortal() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("portal", "admin");
        MockHttpServletResponse response = new MockHttpServletResponse();
        var authentication = new TestingAuthenticationToken("booking-admin", "hash", "ROLE_ADMIN");

        handler.onAuthenticationSuccess(request, response, authentication);

        assertEquals("/admin/dashboard", response.getRedirectedUrl());
    }
}
