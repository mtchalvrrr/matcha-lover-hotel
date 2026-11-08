package com.hotel.system.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class PaymentModeModelAdvice {
    private final String paymentMode;
    public PaymentModeModelAdvice(@Value("${payment.mode:SIMULATED}") String paymentMode) {
        this.paymentMode = paymentMode;
    }
    @ModelAttribute("paymentMode")
    public String paymentMode() { return paymentMode; }
}
