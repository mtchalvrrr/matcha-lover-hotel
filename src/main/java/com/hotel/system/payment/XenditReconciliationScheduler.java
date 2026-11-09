package com.hotel.system.payment;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class XenditReconciliationScheduler {
    private final XenditService xendit;

    public XenditReconciliationScheduler(XenditService xendit) {
        this.xendit = xendit;
    }

    @Scheduled(fixedDelayString = "${payment.xendit.reconcile-ms:60000}")
    public void reconcilePendingPayments() {
        xendit.reconcilePendingCheckouts();
    }
}
