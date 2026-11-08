package com.hotel.system.security;

import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Enforces the persisted failed-login lockout before password verification.
 * The failure handler records unsuccessful attempts; successful authentication
 * clears the counter in the success handler.
 */
public class LoginAttemptAuthenticationProvider extends DaoAuthenticationProvider {
    private final LoginAttemptService loginAttempts;

    public LoginAttemptAuthenticationProvider(UserDetailsService userDetailsService,
                                               PasswordEncoder passwordEncoder,
                                               LoginAttemptService loginAttempts) {
        super(userDetailsService);
        setPasswordEncoder(passwordEncoder);
        this.loginAttempts = loginAttempts;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication == null ? null : authentication.getName();
        if (loginAttempts.isLocked(username)) {
            throw new LockedException("This account is temporarily locked after repeated failed sign-in attempts. Try again in 15 minutes.");
        }
        return super.authenticate(authentication);
    }
}
