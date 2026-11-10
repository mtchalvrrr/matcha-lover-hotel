package com.hotel.system.security;

import org.springframework.security.authentication.LockedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Enforces CAPTCHA verification and persisted failed-password lockout before
 * allowing Spring Security to verify the submitted username and password.
 * CAPTCHA failures are deliberately distinguished from password failures so
 * that an unsolved CAPTCHA does not lock a legitimate user out of the account.
 */
public class LoginAttemptAuthenticationProvider extends DaoAuthenticationProvider {
    private final LoginAttemptService loginAttempts;
    private final CaptchaService captcha;

    public LoginAttemptAuthenticationProvider(UserDetailsService userDetailsService,
                                               PasswordEncoder passwordEncoder,
                                               LoginAttemptService loginAttempts) {
        this(userDetailsService, passwordEncoder, loginAttempts, null);
    }

    public LoginAttemptAuthenticationProvider(UserDetailsService userDetailsService,
                                               PasswordEncoder passwordEncoder,
                                               LoginAttemptService loginAttempts,
                                               CaptchaService captcha) {
        super(passwordEncoder);
        setUserDetailsService(userDetailsService);
        this.loginAttempts = loginAttempts;
        this.captcha = captcha;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication == null ? null : authentication.getName();
        if (captcha != null && captcha.isEnabled()) {
            var attributes = RequestContextHolder.getRequestAttributes();
            if (!(attributes instanceof ServletRequestAttributes servlet)
                    || !captcha.verify(servlet.getRequest().getParameter("g-recaptcha-response"),
                            servlet.getRequest().getRemoteAddr())) {
                throw new CaptchaVerificationException("CAPTCHA verification failed. Please try again.");
            }
        }
        if (loginAttempts.isLocked(username)) {
            throw new LockedException("This account is temporarily locked after repeated failed sign-in attempts. Try again in 15 minutes.");
        }
        return super.authenticate(authentication);
    }
}
