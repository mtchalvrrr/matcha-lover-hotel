package com.hotel.system.account;

import com.hotel.system.payment.PaymentService;
import com.hotel.system.reservation.ReservationExtensionService;
import com.hotel.system.reservation.ReservationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SuperAdminRecordsController {
    private final ReservationService reservations;
    private final ReservationExtensionService extensions;
    private final PaymentService payments;

    public SuperAdminRecordsController(ReservationService reservations,
            ReservationExtensionService extensions, PaymentService payments) {
        this.reservations = reservations;
        this.extensions = extensions;
        this.payments = payments;
    }

    @GetMapping("/super-admin/reservations")
    public String reservations(Model model) {
        model.addAttribute("reservations", reservations.allReservations());
        model.addAttribute("extensions", extensions.allRequests());
        return "super-admin/reservations";
    }

    @GetMapping("/super-admin/payments")
    public String payments(Model model) {
        model.addAttribute("payments", payments.allPayments());
        model.addAttribute("refundRequests", payments.allRefundRequests());
        return "super-admin/payments";
    }
}
