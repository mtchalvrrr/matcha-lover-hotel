package com.hotel.system.account;

import com.hotel.system.audit.AuditService;
import com.hotel.system.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AccountDeactivationService {
    private final AccountDeactivationRequestRepository requests;
    private final SystemUserRepository users;
    private final AuditService audit;
    private final NotificationService notifications;
    private final SessionRegistry sessionRegistry;

    public AccountDeactivationService(AccountDeactivationRequestRepository requests,
                                      SystemUserRepository users, AuditService audit,
                                      NotificationService notifications, SessionRegistry sessionRegistry) {
        this.requests = requests;
        this.users = users;
        this.audit = audit;
        this.notifications = notifications;
        this.sessionRegistry = sessionRegistry;
    }

    public AccountDeactivationRequest request(String username, String reason) {
        SystemUser user = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Customer account not found."));
        if (user.getRole() != AccountRole.CUSTOMER || !user.isActive()) {
            throw new IllegalStateException("Only an active customer account can request deactivation.");
        }
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isBlank() || cleanReason.length() > 1000) {
            throw new IllegalArgumentException("Enter a reason up to 1,000 characters.");
        }
        if (requests.existsByUserIdAndStatus(user.getId(), DeactivationRequestStatus.PENDING)) {
            throw new IllegalStateException("You already have a pending deactivation request.");
        }
        AccountDeactivationRequest saved = requests.save(
                new AccountDeactivationRequest(user.getId(), user.getUsername(), cleanReason));
        audit.record(username, "ACCOUNT_DEACTIVATION_REQUESTED", "SYSTEM_USER", user.getId().toString(),
                "Customer requested account deactivation.");
        notifications.notifySuperAdmins("Customer deactivation request",
                "Customer " + username + " requested account deactivation.", "ACCOUNT");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AccountDeactivationRequest> customerRequests(String username) {
        return requests.findByUsernameOrderByRequestedAtDesc(username);
    }

    @Transactional(readOnly = true)
    public List<AccountDeactivationRequest> pendingRequests() {
        return requests.findByStatusOrderByRequestedAtAsc(DeactivationRequestStatus.PENDING);
    }

    public void review(Long requestId, boolean approve, String reviewer, String reason) {
        AccountDeactivationRequest request = requests.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Deactivation request not found."));
        if (!approve && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("A reason is required when rejecting a deactivation request.");
        }
        SystemUser user = users.findById(request.getUserId())
                .orElseThrow(() -> new IllegalStateException("The customer account no longer exists."));
        request.review(approve, reviewer, reason);
        if (approve) {
            user.setActive(false);
            expireSessions(user.getUsername());
            audit.record(reviewer, "CUSTOMER_ACCOUNT_DEACTIVATED", "SYSTEM_USER", user.getId().toString(),
                    "Approved deactivation request #" + requestId + " for " + user.getUsername());
            notifications.notifyUser(user.getUsername(), "Account deactivated",
                    "Your account deactivation request was approved. You can no longer sign in.", "ACCOUNT");
        } else {
            audit.record(reviewer, "CUSTOMER_DEACTIVATION_REJECTED", "SYSTEM_USER", user.getId().toString(),
                    "Rejected deactivation request #" + requestId + ". " + reason.trim());
            notifications.notifyUser(user.getUsername(), "Deactivation request declined",
                    "Your account deactivation request was declined. Reason: " + reason.trim(), "ACCOUNT");
        }
    }

    private void expireSessions(String username) {
        sessionRegistry.getAllPrincipals().stream()
                .filter(principal -> principal instanceof UserDetails details
                        && details.getUsername().equalsIgnoreCase(username))
                .forEach(principal -> sessionRegistry.getAllSessions(principal, false)
                        .forEach(session -> session.expireNow()));
    }
}
