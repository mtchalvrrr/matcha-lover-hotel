package com.hotel.system.payment;

import jakarta.persistence.*;
import java.time.Instant;

/** Stores only the safe online-checkout enable/disable switch; provider secrets remain environment-only. */
@Entity
@Table(name = "payment_provider_settings")
public class PaymentProviderSetting {
    @Id
    private Long id = 1L;

    @Column(name = "online_payments_enabled", nullable = false)
    private boolean onlinePaymentsEnabled = true;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PaymentProviderSetting() {}

    public Long getId() { return id; }
    public boolean isOnlinePaymentsEnabled() { return onlinePaymentsEnabled; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(boolean enabled, String actor) {
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("An administrator identity is required.");
        onlinePaymentsEnabled = enabled;
        updatedBy = actor;
        updatedAt = Instant.now();
    }
}