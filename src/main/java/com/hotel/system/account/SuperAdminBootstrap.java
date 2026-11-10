package com.hotel.system.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Creates or repairs the explicitly configured Super Admin account at startup.
 *
 * The account is identified by BOTH configured username and email. If either
 * value belongs to a different account, startup fails instead of changing that
 * account's role or credentials. When both values identify the same
 * SUPER_ADMIN account, its password hash is refreshed using the configured
 * password and the account is reactivated.
 *
 * Keep HOTEL_SUPER_ADMIN_PASSWORD in Codespaces/environment secrets. Never
 * commit the actual password to the repository.
 */
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

        // Do not create or modify a privileged account unless all three
        // bootstrap settings have been deliberately configured.
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

        String normalizedUsername = username.trim();
        String normalizedEmail = email.trim().toLowerCase();

        SystemUser usernameMatch = users.findByUsernameIgnoreCase(normalizedUsername).orElse(null);
        SystemUser emailMatch = users.findByEmailIgnoreCase(normalizedEmail).orElse(null);

        if (usernameMatch == null && emailMatch == null) {
            // No account uses either identifier, so create the configured
            // Super Admin with a BCrypt password hash.
            users.save(new SystemUser(
                    normalizedUsername,
                    normalizedEmail,
                    passwordEncoder.encode(password),
                    AccountRole.SUPER_ADMIN));
            return;
        }

        // Both identifiers must resolve to the exact same existing account.
        // This prevents accidentally taking over a customer or admin account.
        if (usernameMatch == null || emailMatch == null
                || !usernameMatch.getId().equals(emailMatch.getId())) {
            throw new IllegalStateException(
                    "Super Admin bootstrap stopped: the configured username and email do not identify the same account. Resolve the account conflict before starting the application.");
        }

        if (usernameMatch.getRole() != AccountRole.SUPER_ADMIN) {
            throw new IllegalStateException(
                    "Super Admin bootstrap stopped: the configured account does not have the SUPER_ADMIN role. Its role was not changed.");
        }

        // Repair an existing configured Super Admin account. This handles an
        // invalid/old password hash and an account that was disabled, without
        // changing its identity, email, or assigned role.
        usernameMatch.setPasswordHash(passwordEncoder.encode(password));
        usernameMatch.setActive(true);
        users.save(usernameMatch);
    }
}
