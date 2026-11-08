package com.hotel.system.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginAttemptAuthenticationProviderTest {
    @Test
    void rejectsAuthenticationWhenAccountIsTemporarilyLocked() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        UserDetailsService users = username -> User.withUsername(username)
                .password(encoder.encode("Correct-Password-123!"))
                .authorities("ROLE_CUSTOMER")
                .build();
        LoginAttemptService attempts = mock(LoginAttemptService.class);
        when(attempts.isLocked("guest")).thenReturn(true);

        LoginAttemptAuthenticationProvider provider =
                new LoginAttemptAuthenticationProvider(users, encoder, attempts);

        assertThrows(LockedException.class, () -> provider.authenticate(
                new UsernamePasswordAuthenticationToken("guest", "Correct-Password-123!")));
        verify(attempts).isLocked("guest");
    }

    @Test
    void delegatesToPasswordAuthenticationWhenAccountIsNotLocked() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        String encoded = encoder.encode("Correct-Password-123!");
        UserDetailsService users = username -> User.withUsername(username)
                .password(encoded)
                .authorities("ROLE_CUSTOMER")
                .build();
        LoginAttemptService attempts = mock(LoginAttemptService.class);
        when(attempts.isLocked("guest")).thenReturn(false);

        LoginAttemptAuthenticationProvider provider =
                new LoginAttemptAuthenticationProvider(users, encoder, attempts);

        var result = provider.authenticate(
                new UsernamePasswordAuthenticationToken("guest", "Correct-Password-123!"));

        assertTrue(result.isAuthenticated());
        assertEquals("guest", result.getName());
    }
}
