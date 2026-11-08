package com.hotel.system.account;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final SystemUserRepository users;

    public DatabaseUserDetailsService(SystemUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SystemUser account = users.findByUsernameIgnoreCase(username.trim())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));

        return User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .authorities("ROLE_" + account.getRole().name())
                .disabled(!account.isActive())
                .build();
    }
}
