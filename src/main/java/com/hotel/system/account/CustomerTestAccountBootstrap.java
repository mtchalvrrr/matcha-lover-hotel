package com.hotel.system.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates or refreshes three separate portal test accounts only when the
 * explicit test-account Spring profile is active.
 *
 * The short shared password is intentionally limited to local testing.
 * Never activate this profile in a live or publicly accessible environment.
 */
@Component
@Profile("test-account")
public class CustomerTestAccountBootstrap implements ApplicationRunner {
    private final SystemUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String password;
    private final String customerUsername;
    private final String customerEmail;
    private final String managerUsername;
    private final String managerEmail;
    private final String superAdminUsername;
    private final String superAdminEmail;

    public CustomerTestAccountBootstrap(
            SystemUserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${HOTEL_TEST_PORTAL_PASSWORD:pass}") String password,
            @Value("${HOTEL_TEST_CUSTOMER_USERNAME:cedih}") String customerUsername,
            @Value("${HOTEL_TEST_CUSTOMER_EMAIL:cedih.test@matchalover.local}") String customerEmail,
            @Value("${HOTEL_TEST_MANAGER_USERNAME:cedih_manager}") String managerUsername,
            @Value("${HOTEL_TEST_MANAGER_EMAIL:cedih.manager@matchalover.local}") String managerEmail,
            @Value("${HOTEL_TEST_SUPER_ADMIN_USERNAME:cedih_superadmin}") String superAdminUsername,
            @Value("${HOTEL_TEST_SUPER_ADMIN_EMAIL:cedih.superadmin@matchalover.local}") String superAdminEmail) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.password = password;
        this.customerUsername = customerUsername.trim();
        this.customerEmail = customerEmail.trim().toLowerCase();
        this.managerUsername = managerUsername.trim();
        this.managerEmail = managerEmail.trim().toLowerCase();
        this.superAdminUsername = superAdminUsername.trim();
        this.superAdminEmail = superAdminEmail.trim().toLowerCase();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException("The local portal test password must not be blank.");
        }

        // These usernames must be distinct because username is globally unique.
        if (customerUsername.equalsIgnoreCase(managerUsername)
                || customerUsername.equalsIgnoreCase(superAdminUsername)
                || managerUsername.equalsIgnoreCase(superAdminUsername)) {
            throw new IllegalStateException("Each test portal account must have a different username.");
        }

        ensureAccount("Test", "Customer", customerUsername, customerEmail, AccountRole.CUSTOMER);
        ensureAccount("Test", "Manager", managerUsername, managerEmail, AccountRole.ADMIN);
        ensureAccount("Test", "Super Admin", superAdminUsername, superAdminEmail, AccountRole.SUPER_ADMIN);
    }

    /**
     * Creates a missing test account or refreshes its local test password.
     * It refuses to change the role of an existing account.
     */
    private void ensureAccount(String firstName, String lastName, String username,
                               String email, AccountRole requiredRole) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(email)) {
            throw new IllegalStateException("Every test portal account needs a username and email.");
        }

        SystemUser existing = users.findByUsernameIgnoreCase(username).orElse(null);
        if (existing != null) {
            if (existing.getRole() != requiredRole) {
                throw new IllegalStateException("Test username '" + username
                        + "' already belongs to a different role. No account was changed.");
            }
            existing.setPasswordHash(passwordEncoder.encode(password));
            existing.setActive(true);
            users.save(existing);
            return;
        }

        if (users.existsByEmailIgnoreCase(email)) {
            throw new IllegalStateException("Test email '" + email
                    + "' is already used by another account. No account was created.");
        }

        users.save(new SystemUser(firstName, lastName, username, email,
                passwordEncoder.encode(password), requiredRole));
    }
}
