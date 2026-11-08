package com.hotel.system.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.session.web.http.HttpSessionIdResolver;

import java.util.List;

/**
 * Keeps each portal's session identifier in a separate browser cookie.
 * The request path selects exactly one cookie, so a portal cannot reuse
 * another portal's authenticated session.
 */
public final class PortalSessionIdResolver implements HttpSessionIdResolver {
    public static final String CUSTOMER_COOKIE = "MATCHA_CUSTOMER_SESSION";
    public static final String ADMIN_COOKIE = "MATCHA_ADMIN_SESSION";
    public static final String SUPER_ADMIN_COOKIE = "MATCHA_SUPER_ADMIN_SESSION";

    private final boolean secure;

    public PortalSessionIdResolver(boolean secure) {
        this.secure = secure;
    }

    @Override
    public List<String> resolveSessionIds(HttpServletRequest request) {
        String expected = cookieNameFor(request.getRequestURI());
        if (expected == null || request.getCookies() == null) return List.of();
        for (Cookie cookie : request.getCookies()) {
            if (expected.equals(cookie.getName()) && cookie.getValue() != null
                    && !cookie.getValue().isBlank()) {
                return List.of(cookie.getValue());
            }
        }
        return List.of();
    }

    @Override
    public void setSessionId(HttpServletRequest request, HttpServletResponse response, String sessionId) {
        writeCookie(response, cookieNameFor(request.getRequestURI()), sessionId, false);
    }

    @Override
    public void expireSession(HttpServletRequest request, HttpServletResponse response) {
        writeCookie(response, cookieNameFor(request.getRequestURI()), "", true);
    }

    private String cookieNameFor(String uri) {
        if (uri == null) return null;
        if (uri.equals("/admin") || uri.startsWith("/admin/")) return ADMIN_COOKIE;
        if (uri.equals("/super-admin") || uri.startsWith("/super-admin/")) return SUPER_ADMIN_COOKIE;
        // Customer session is also used for the public room catalogue and guest chatbot context.
        return CUSTOMER_COOKIE;
    }

    private void writeCookie(HttpServletResponse response, String name, String value, boolean expire) {
        if (name == null) return;
        StringBuilder cookie = new StringBuilder(name).append('=').append(value == null ? "" : value)
                .append("; Path=/; HttpOnly; SameSite=Lax");
        if (secure) cookie.append("; Secure");
        if (expire) cookie.append("; Max-Age=0");
        response.addHeader(HttpServletResponseHeader.SET_COOKIE, cookie.toString());
    }

    private static final class HttpServletResponseHeader {
        private static final String SET_COOKIE = "Set-Cookie";
    }
}
