package com.hotel.system.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "admin_permission_settings")
public class AdminPermissionSetting {
    @Id
    @Column(name = "permission_key", length = 50)
    private String permissionKey;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "updated_by", length = 80)
    private String updatedBy;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected AdminPermissionSetting() { }

    public AdminPermissionSetting(String permissionKey, boolean enabled, String updatedBy) {
        this.permissionKey = permissionKey;
        this.enabled = enabled;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }
    public String getPermissionKey() { return permissionKey; }
    public boolean isEnabled() { return enabled; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void update(boolean enabled, String actor) {
        this.enabled = enabled;
        this.updatedBy = actor;
        this.updatedAt = Instant.now();
    }
}
