package com.hotel.system.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

@Component
public class PortalAuthenticationFailureHandler implements AuthenticationFailureHandler {
    private static final Set<String> PORTALS = Set.of("customer", "admin", "super-admin");
    private final LoginAttemptService loginAttempts;

    public PortalAuthenticationFailureHandler(LoginAttemptService loginAttempts) {
        this.loginAttempts = loginAttempts;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String portal = request.getParameter("portal");
        if (!PORTALS.contains(portal)) {
            portal = "customer";
        }

        // Match the older system's separate CAPTCHA feedback. A CAPTCHA error
        // is not a bad-password attempt and must not increment account lockout.
        if (exception instanceof CaptchaVerificationException) {
            response.sendRedirect("/" + portal + "/login?captchaError");
            return;
        }

        loginAttempts.recordFailure(request.getParameter("username"));
        response.sendRedirect("/" + portal + "/login?error");
    }
}
