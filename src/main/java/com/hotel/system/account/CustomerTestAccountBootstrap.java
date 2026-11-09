package com.hotel.system.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Creates an explicitly requested local customer account for manual testing.
 * No account is created unless HOTEL_TEST_CUSTOMER_PASSWORD is supplied.
 */
@Component
public class CustomerTestAccountBootstrap implements ApplicationRunner {
    private final SystemUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String password;
    private final String username;
    private final String email;

    public CustomerTestAccountBootstrap(
            SystemUserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${HOTEL_TEST_CUSTOMER_PASSWORD:}") String password,
            @Value("${HOTEL_TEST_CUSTOMER_USERNAME:customer_test}") String username,
            @Value("${HOTEL_TEST_CUSTOMER_EMAIL:customer.test@matchalover.local}") String email) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.password = password;
        this.username = username.trim();
        this.email = email.trim().toLowerCase();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(password)) {
            return;
        }
        if (password.length() < 12
                || !password.matches(".*[A-Z].*")
                || !password.matches(".*[a-z].*")
                || !password.matches(".*\\d.*")
                || !password.matches(".*[^A-Za-z0-9].*")) {
            throw new IllegalStateException(
                    "HOTEL_TEST_CUSTOMER_PASSWORD must be at least 12 characters and include uppercase, lowercase, a number, and a special character.");
        }
        if (users.existsByUsernameIgnoreCase(username) || users.existsByEmailIgnoreCase(email)) {
            return;
        }
        users.save(new SystemUser("Test", "Customer", username, email,
                passwordEncoder.encode(password), AccountRole.CUSTOMER));
    }
}
