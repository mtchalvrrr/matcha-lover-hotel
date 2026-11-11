package com.hotel.system.security;

import org.springframework.security.core.AuthenticationException;

/**
 * Identifies a failed CAPTCHA challenge separately from incorrect credentials.
 * CAPTCHA failures must not consume a user's password-attempt allowance.
 */
public class CaptchaVerificationException extends AuthenticationException {
    public CaptchaVerificationException(String message) {
        super(message);
    }
}
