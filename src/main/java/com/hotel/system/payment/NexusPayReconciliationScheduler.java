package com.hotel.system.payment;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls NexusPay for pending sandbox payment results if a webhook is delayed. */
@Component
public class NexusPayReconciliationScheduler {
    private final NexusPayService nexusPay;

    public NexusPayReconciliationScheduler(NexusPayService nexusPay) {
        this.nexusPay = nexusPay;
    }

    @Scheduled(fixedDelayString = "${payment.nexuspay.reconcile-ms:60000}")
    public void reconcilePendingPayments() {
        nexusPay.reconcilePendingCheckouts();
    }
}
