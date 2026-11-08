package com.hotel.system.payment;

import com.hotel.system.audit.AuditService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PaymentController {
    private final PaymentService payments;
    private final AuditService audit;

    public PaymentController(PaymentService payments, AuditService audit) {
        this.payments = payments;
        this.audit = audit;
    }

    @PostMapping("/customer/reservations/{id}/pay")
    public String pay(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
        try {
            Payment payment = payments.payNextDue(id, authentication.getName());
            redirect.addFlashAttribute("success", "Simulated payment recorded. Reference: " + payment.getTransactionReference());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/customer/reservations";
    }

    @GetMapping("/customer/payments")
    public String customerPayments(Authentication authentication, Model model) {
        model.addAttribute("payments", payments.customerPayments(authentication.getName()));
        return "customer/payments";
    }

    @GetMapping("/admin/payments")
    public String adminPayments(Model model) {
        model.addAttribute("payments", payments.allPayments());
        return "admin/payments/list";
    }

    @PostMapping("/admin/payments/{id}/refund")
    public String refund(@PathVariable Long id, @RequestParam String reason,
                         Authentication authentication, RedirectAttributes redirect) {
        try {
            payments.refundPayment(id, authentication.getName(), reason);
            redirect.addFlashAttribute("success", "Payment marked as refunded.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/payments";
    }

    @PostMapping("/admin/payments/{id}/void")
    public String voidPayment(@PathVariable Long id, @RequestParam String reason,
                              Authentication authentication, RedirectAttributes redirect) {
        try {
            payments.voidPendingPayment(id, authentication.getName(), reason);
            redirect.addFlashAttribute("success", "Pending payment voided.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/payments";
    }
}