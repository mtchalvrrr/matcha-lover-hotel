package com.hotel.system.account;

import com.hotel.system.audit.AuditService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional
public class AccountManagementService {
    private final SystemUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final SessionRegistry sessionRegistry;

    public AccountManagementService(SystemUserRepository users, PasswordEncoder encoder, AuditService audit,
                                    SessionRegistry sessionRegistry) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.sessionRegistry = sessionRegistry;
    }

    @Transactional(readOnly = true)
    public List<SystemUser> manageableAccounts() {
        return users.findByRoleInOrderByCreatedAtDesc(List.of(AccountRole.ADMIN, AccountRole.CUSTOMER));
    }

    public SystemUser createAdmin(String actor, String username, String email, String password) {
        String cleanUsername = username == null ? "" : username.trim();
        String cleanEmail = email == null ? "" : email.trim().toLowerCase();
        validateCredentials(cleanUsername, cleanEmail, password);
        if (users.existsByUsernameIgnoreCase(cleanUsername)) throw new IllegalArgumentException("Username is already in use.");
        if (users.existsByEmailIgnoreCase(cleanEmail)) throw new IllegalArgumentException("Email is already in use.");
        SystemUser admin = users.save(new SystemUser(cleanUsername, cleanEmail, encoder.encode(password), AccountRole.ADMIN));
        audit.record(actor, "ADMIN_ACCOUNT_CREATED", "SYSTEM_USER", admin.getId().toString(),
            "Created ADMIN account " + cleanUsername);
        return admin;
    }

    public void resetAdminPassword(String actor, Long id, String newPassword) {
        SystemUser target = users.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));
        if (target.getRole() != AccountRole.ADMIN)
            throw new IllegalArgumentException("Only Admin account passwords can be reset here.");
        validateCredentials(target.getUsername(), target.getEmail(), newPassword);
        target.setPasswordHash(encoder.encode(newPassword));
        expireSessions(target.getUsername());
        audit.record(actor, "ADMIN_PASSWORD_RESET", "SYSTEM_USER", id.toString(),
                "Reset password for Admin account " + target.getUsername() + "; active sessions expired.");
    }

    public void setAccountActive(String actor, Long id, boolean active) {
        SystemUser target = users.findById(id).orElseThrow(() -> new IllegalArgumentException("Account not found."));
        if (target.getRole() == AccountRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("Super Admin accounts cannot be changed through this screen.");
        }
        if (target.getUsername().equalsIgnoreCase(actor)) {
            throw new IllegalArgumentException("You cannot deactivate your own account.");
        }
        if (target.isActive() == active) return;
        target.setActive(active);
        if (!active) expireSessions(target.getUsername());
        audit.record(actor, active ? "ACCOUNT_REACTIVATED" : "ACCOUNT_SUSPENDED",
            "SYSTEM_USER", id.toString(), "Changed " + target.getRole() + " account " + target.getUsername());
    }

    private void expireSessions(String username) {
        sessionRegistry.getAllPrincipals().stream()
                .filter(principal -> principal instanceof UserDetails details
                        && details.getUsername().equalsIgnoreCase(username))
                .forEach(principal -> sessionRegistry.getAllSessions(principal, false)
                        .forEach(session -> session.expireNow()));
    }

    private void validateCredentials(String username, String email, String password) {
        if (username.length() < 4 || username.length() > 80) throw new IllegalArgumentException("Username must be 4 to 80 characters.");
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$") || email.length() > 254)
            throw new IllegalArgumentException("Enter a valid email address.");
        if (password == null || password.length() < 12 || !password.matches(".*[A-Z].*")
            || !password.matches(".*[a-z].*") || !password.matches(".*\\d.*")
            || !password.matches(".*[^A-Za-z0-9].*"))
            throw new IllegalArgumentException("Password must be at least 12 characters and include uppercase, lowercase, a number, and a special character.");
    }
}