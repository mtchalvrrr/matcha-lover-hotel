package com.hotel.system.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class PortalAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    private static final Map<String, String> PORTAL_ROLES = Map.of(
            "customer", "ROLE_CUSTOMER",
            "admin", "ROLE_ADMIN",
            "super-admin", "ROLE_SUPER_ADMIN"
    );
    private static final Map<String, String> DESTINATIONS = Map.of(
            "customer", "/customer/dashboard",
            "admin", "/admin/dashboard",
            "super-admin", "/super-admin/dashboard"
    );

    private final LoginAttemptService loginAttempts;

    public PortalAuthenticationSuccessHandler(LoginAttemptService loginAttempts) {
        this.loginAttempts = loginAttempts;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String portal = request.getParameter("portal");
        String requiredRole = PORTAL_ROLES.get(portal);
        if (requiredRole == null || authentication.getAuthorities().stream()
                .noneMatch(authority -> requiredRole.equals(authority.getAuthority()))) {
            request.getSession().invalidate();
            response.sendRedirect("/" + safePortal(portal) + "/login?denied");
            return;
        }
        loginAttempts.recordSuccess(authentication.getName());
        response.sendRedirect(DESTINATIONS.get(portal));
    }

    private String safePortal(String portal) {
        return PORTAL_ROLES.containsKey(portal) ? portal : "customer";
    }
}
