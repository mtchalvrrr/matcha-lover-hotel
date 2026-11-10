package com.hotel.system.payment;

import com.hotel.system.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentProviderSettingsService {
    private final PaymentProviderSettingRepository settings;
    private final AuditService audit;

    public PaymentProviderSettingsService(PaymentProviderSettingRepository settings, AuditService audit) {
        this.settings = settings;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public boolean onlinePaymentsEnabled() {
        return settings.findById(1L).map(PaymentProviderSetting::isOnlinePaymentsEnabled).orElse(true);
    }

    public void updateOnlinePayments(boolean enabled, String actor) {
        PaymentProviderSetting setting = settings.findById(1L).orElseGet(PaymentProviderSetting::new);
        boolean previous = setting.isOnlinePaymentsEnabled();
        setting.update(enabled, actor);
        settings.save(setting);
        if (previous != enabled) {
            audit.record(actor, "ONLINE_PAYMENT_AVAILABILITY_CHANGED", "PAYMENT_SETTINGS", "1",
                    "Online payment checkout changed from " + previous + " to " + enabled
                            + ". Provider secrets and payment mode remain environment-managed.");
        }
    }
}