package com.hotel.system.account;

import com.hotel.system.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Arrays;
import java.util.List;

@Service("adminPermissionService")
@Transactional
public class AdminPermissionService {
    public enum Permission {
        RESERVATION_EDIT("Edit reservations", "RESERVATION_EDIT"),
        AUDIT_VIEW("View audit logs", "AUDIT_VIEW"),
        REPORT_VIEW("View reports", "REPORT_VIEW"),
        PAYMENT_MANAGE("Manage payments", "PAYMENT_MANAGE"),
        CUSTOMER_MANAGE("Manage customers", "CUSTOMER_MANAGE"),
        ROOM_MANAGE("Manage rooms", "ROOM_MANAGE"),
        NOTIFICATION_MANAGE("Manage notifications", "NOTIFICATION_MANAGE");

        private final String label;
        private final String key;
        Permission(String label, String key) { this.label = label; this.key = key; }
        public String getLabel() { return label; }
        public String getKey() { return key; }
    }

    private final AdminPermissionRepository repository;
    private final AuditService audit;

    public AdminPermissionService(AdminPermissionRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(String key) {
        Permission permission = permission(key);
        return repository.findById(permission.getKey()).map(AdminPermissionSetting::isEnabled).orElse(true);
    }

    @Transactional(readOnly = true)
    public List<PermissionView> all() {
        return Arrays.stream(Permission.values())
                .map(permission -> new PermissionView(permission.getKey(), permission.getLabel(),
                        isEnabled(permission.getKey())))
                .toList();
    }

    public void update(String key, boolean enabled, String actor) {
        Permission permission = permission(key);
        AdminPermissionSetting setting = repository.findById(permission.getKey())
                .orElseGet(() -> new AdminPermissionSetting(permission.getKey(), true, actor));
        boolean previous = setting.isEnabled();
        setting.update(enabled, actor);
        repository.save(setting);
        if (previous != enabled) {
            audit.record(actor, "ADMIN_PERMISSION_CHANGED", "ADMIN_PERMISSION", permission.getKey(),
                    permission.getLabel() + " changed from " + previous + " to " + enabled
                    + ". This applies equally to both Admin accounts.");
        }
    }

    private Permission permission(String key) {
        return Arrays.stream(Permission.values()).filter(value -> value.getKey().equals(key))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown Admin permission."));
    }

    public record PermissionView(String key, String label, boolean enabled) { }
}
