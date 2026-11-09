package com.hotel.system.account;

import com.hotel.system.payment.PaymentService;
import com.hotel.system.reservation.ReservationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SuperAdminRecordsController {
    private final ReservationService reservations;
    private final PaymentService payments;

    public SuperAdminRecordsController(ReservationService reservations, PaymentService payments) {
        this.reservations = reservations;
        this.payments = payments;
    }

    @GetMapping("/super-admin/reservations")
    public String reservations(Model model) {
        model.addAttribute("reservations", reservations.allReservations());
        return "super-admin/reservations";
    }

    @GetMapping("/super-admin/payments")
    public String payments(Model model) {
        model.addAttribute("payments", payments.allPayments());
        model.addAttribute("refundRequests", payments.allRefundRequests());
        return "super-admin/payments";
    }
}
