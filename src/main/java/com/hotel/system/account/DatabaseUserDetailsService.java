package com.hotel.system.account;

import com.hotel.system.security.LoginAttemptService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final SystemUserRepository users;
    private final LoginAttemptService loginAttempts;

    public DatabaseUserDetailsService(SystemUserRepository users, LoginAttemptService loginAttempts) {
        this.users = users;
        this.loginAttempts = loginAttempts;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String submittedUsername = username == null ? "" : username.trim();
        SystemUser account = users.findByUsernameIgnoreCase(submittedUsername)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));

        return User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .authorities("ROLE_" + account.getRole().name())
                .disabled(!account.isActive())
                .accountLocked(loginAttempts.isLocked(submittedUsername))
                .build();
    }
}
