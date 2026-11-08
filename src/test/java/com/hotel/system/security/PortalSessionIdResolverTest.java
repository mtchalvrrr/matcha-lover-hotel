package com.hotel.system.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalSessionIdResolverTest {
    private final PortalSessionIdResolver resolver = new PortalSessionIdResolver(false);

    @Test
    void customerRequestsResolveOnlyCustomerSessionCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/customer/dashboard");
        request.setCookies(
                new Cookie(PortalSessionIdResolver.ADMIN_COOKIE, "admin-session"),
                new Cookie(PortalSessionIdResolver.CUSTOMER_COOKIE, "customer-session"),
                new Cookie(PortalSessionIdResolver.SUPER_ADMIN_COOKIE, "super-session"));

        assertEquals(List.of("customer-session"), resolver.resolveSessionIds(request));
    }

    @Test
    void adminAndSuperAdminRequestsResolveTheirOwnSessionCookies() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(PortalSessionIdResolver.ADMIN_COOKIE, "admin-session"),
                new Cookie(PortalSessionIdResolver.CUSTOMER_COOKIE, "customer-session"),
                new Cookie(PortalSessionIdResolver.SUPER_ADMIN_COOKIE, "super-session"));

        request.setRequestURI("/admin/dashboard");
        assertEquals(List.of("admin-session"), resolver.resolveSessionIds(request));

        request.setRequestURI("/super-admin/dashboard");
        assertEquals(List.of("super-session"), resolver.resolveSessionIds(request));
    }

    @Test
    void logoutExpiresOnlyTheCookieForTheRequestedPortal() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/admin/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();

        resolver.expireSession(request, response);

        String cookie = response.getHeader("Set-Cookie");
        assertTrue(cookie.startsWith(PortalSessionIdResolver.ADMIN_COOKIE + "="));
        assertTrue(cookie.contains("Max-Age=0"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
    }
}
