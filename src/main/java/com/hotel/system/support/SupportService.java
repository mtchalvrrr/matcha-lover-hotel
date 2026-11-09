package com.hotel.system.support;

import com.hotel.system.account.AccountRole;
import com.hotel.system.account.SystemUser;
import com.hotel.system.account.SystemUserRepository;
import com.hotel.system.audit.AuditService;
import com.hotel.system.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class SupportService {
    private static final List<String> CATEGORIES =
            List.of("GENERAL", "RESERVATION", "PAYMENT", "ROOM", "ACCOUNT");

    private final SupportCaseRepository cases;
    private final SupportMessageRepository messages;
    private final SystemUserRepository users;
    private final NotificationService notifications;
    private final AuditService audit;

    public SupportService(SupportCaseRepository cases, SupportMessageRepository messages,
                          SystemUserRepository users, NotificationService notifications,
                          AuditService audit) {
        this.cases = cases;
        this.messages = messages;
        this.users = users;
        this.notifications = notifications;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<SupportThread> customerThreads(String username) {
        return cases.findByCustomerUsernameAndVisibleTrueOrderByUpdatedAtDesc(username)
                .stream().map(this::thread).toList();
    }

    @Transactional(readOnly = true)
    public List<SupportThread> managerThreads() {
        return cases.findByVisibleTrueOrderByUpdatedAtDesc().stream().map(this::thread).toList();
    }

    public Long createCase(String username, String category, String subject, String message) {
        requireActiveUser(username, AccountRole.CUSTOMER);
        String cleanCategory = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
        String cleanSubject = clean(subject, 120, "Enter a subject up to 120 characters.");
        String cleanMessage = clean(message, 2000, "Enter a message up to 2,000 characters.");
        if (!CATEGORIES.contains(cleanCategory)) {
            throw new IllegalArgumentException("Choose a valid support category.");
        }

        SupportCase supportCase = cases.saveAndFlush(new SupportCase(username, cleanCategory, cleanSubject));
        SupportMessage firstMessage = messages.save(new SupportMessage(
                supportCase.getId(), username, "CUSTOMER", cleanMessage));
        notifications.notifyAdminsInAppOnly("New customer support request",
                cleanSubject, "SUPPORT", "support-case:" + supportCase.getId() + ":created");
        audit.record(username, "SUPPORT_CASE_CREATED", "SUPPORT_CASE",
                supportCase.getId().toString(), "Customer submitted a support request.");
        return supportCase.getId();
    }

    public void customerReply(Long caseId, String username, String message) {
        SupportCase supportCase = cases.findByIdAndCustomerUsernameAndVisibleTrue(caseId, username)
                .orElseThrow(() -> new IllegalArgumentException("Support conversation not found."));
        String cleanMessage = clean(message, 2000, "Enter a message up to 2,000 characters.");
        SupportMessage saved = messages.save(new SupportMessage(caseId, username, "CUSTOMER", cleanMessage));
        supportCase.recordActivity();
        if (supportCase.getStatus() == SupportStatus.RESOLVED) supportCase.setStatus(SupportStatus.OPEN);
        notifications.notifyAdminsInAppOnly("Customer replied to support request",
                supportCase.getSubject(), "SUPPORT", "support-message:" + saved.getId());
        audit.record(username, "SUPPORT_CUSTOMER_REPLIED", "SUPPORT_CASE",
                caseId.toString(), "Customer sent a support reply.");
    }

    public void managerReply(Long caseId, String managerUsername, String message) {
        requireActiveUser(managerUsername, AccountRole.ADMIN);
        SupportCase supportCase = cases.findByIdAndVisibleTrue(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Support conversation not found."));
        String cleanMessage = clean(message, 2000, "Enter a message up to 2,000 characters.");
        SupportMessage saved = messages.save(new SupportMessage(caseId, managerUsername, "MANAGER", cleanMessage));
        supportCase.recordActivity();
        notifications.notifyUserInAppOnly(supportCase.getCustomerUsername(),
                "Manager replied to your support request", supportCase.getSubject(), "SUPPORT");
        audit.record(managerUsername, "SUPPORT_MANAGER_REPLIED", "SUPPORT_CASE",
                caseId.toString(), "Manager sent a support reply.");
    }

    public void updateStatus(Long caseId, String managerUsername, SupportStatus status) {
        requireActiveUser(managerUsername, AccountRole.ADMIN);
        SupportCase supportCase = cases.findByIdAndVisibleTrue(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Support conversation not found."));
        supportCase.setStatus(status);
        notifications.notifyUserInAppOnly(supportCase.getCustomerUsername(),
                "Support request status updated", supportCase.getSubject() + " — " + status.name(), "SUPPORT");
        audit.record(managerUsername, "SUPPORT_STATUS_CHANGED", "SUPPORT_CASE",
                caseId.toString(), "Manager changed support status to " + status.name() + ".");
    }

    private SupportThread thread(SupportCase supportCase) {
        return new SupportThread(supportCase,
                messages.findBySupportCaseIdOrderByCreatedAtAsc(supportCase.getId()));
    }

    private void requireActiveUser(String username, AccountRole role) {
        SystemUser user = users.findByUsernameIgnoreCase(username)
                .filter(SystemUser::isActive)
                .filter(account -> account.getRole() == role)
                .orElseThrow(() -> new IllegalArgumentException("Your account is not allowed to use this support action."));
    }

    private String clean(String value, int maxLength, String error) {
        String clean = value == null ? "" : value.trim();
        if (clean.isBlank() || clean.length() > maxLength) throw new IllegalArgumentException(error);
        return clean;
    }
}
