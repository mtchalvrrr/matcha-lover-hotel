package com.hotel.system.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Creates or refreshes a dedicated customer account only when the explicit
 * test-account Spring profile is active. Never activate this profile in a live
 * environment.
 */
@Component
@Profile("test-account")
public class CustomerTestAccountBootstrap implements ApplicationRunner {
    private final SystemUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String password;
    private final String username;
    private final String email;

    public CustomerTestAccountBootstrap(
            SystemUserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${HOTEL_TEST_CUSTOMER_PASSWORD:pass}") String password,
            @Value("${HOTEL_TEST_CUSTOMER_USERNAME:cedih}") String username,
            @Value("${HOTEL_TEST_CUSTOMER_EMAIL:cedih.test@matchalover.local}") String email) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.password = password;
        this.username = username.trim();
        this.email = email.trim().toLowerCase();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(password) || !StringUtils.hasText(username) || !StringUtils.hasText(email)) {
            throw new IllegalStateException("Test customer username, password, and email must not be blank.");
        }

        SystemUser existing = users.findByUsernameIgnoreCase(username).orElse(null);
        if (existing != null) {
            if (existing.getRole() != AccountRole.CUSTOMER) {
                throw new IllegalStateException("The configured test username already belongs to a non-customer account.");
            }
            existing.setPasswordHash(passwordEncoder.encode(password));
            existing.setActive(true);
            users.save(existing);
            return;
        }

        if (users.existsByEmailIgnoreCase(email)) {
            throw new IllegalStateException("The configured test customer email is already used by another account.");
        }

        users.save(new SystemUser("Test", "Customer", username, email,
                passwordEncoder.encode(password), AccountRole.CUSTOMER));
    }
}
