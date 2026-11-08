package com.hotel.system.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SuperAdminBootstrap implements ApplicationRunner {
    private final SystemUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String email;
    private final String password;

    public SuperAdminBootstrap(
            SystemUserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${HOTEL_SUPER_ADMIN_USERNAME:}") String username,
            @Value("${HOTEL_SUPER_ADMIN_EMAIL:}") String email,
            @Value("${HOTEL_SUPER_ADMIN_PASSWORD:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean configured = StringUtils.hasText(username)
                && StringUtils.hasText(email)
                && StringUtils.hasText(password);

        if (!configured) {
            return;
        }
        if (password.length() < 12
                || !password.matches(".*[A-Z].*")
                || !password.matches(".*[a-z].*")
                || !password.matches(".*\\d.*")
                || !password.matches(".*[^A-Za-z0-9].*")) {
            throw new IllegalStateException(
                    "HOTEL_SUPER_ADMIN_PASSWORD must be at least 12 characters and include uppercase, lowercase, a number, and a special character.");
        }
        if (!users.existsByUsernameIgnoreCase(username) && !users.existsByEmailIgnoreCase(email)) {
            users.save(new SystemUser(username.trim(), email.trim().toLowerCase(),
                    passwordEncoder.encode(password), AccountRole.SUPER_ADMIN));
        }
    }
}
