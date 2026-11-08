package com.hotel.system.payment;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PayMongoReconciliationScheduler {
    private final PayMongoService payMongo;

    public PayMongoReconciliationScheduler(PayMongoService payMongo) {
        this.payMongo = payMongo;
    }

    @Scheduled(fixedDelayString = "${payment.paymongo.reconcile-ms:300000}")
    public void reconcilePendingPayments() {
        payMongo.reconcilePendingCheckouts();
    }
}
