package com.hotel.system.account;

import com.hotel.system.audit.AuditService;
import com.hotel.system.payment.PaymentService;
import com.hotel.system.reservation.ReservationExtensionService;
import com.hotel.system.reservation.ReservationService;
import com.hotel.system.room.RoomRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SuperAdminRecordsController {
    private final ReservationService reservations;
    private final PaymentService payments;
    private final SystemUserRepository users;
    private final RoomRepository rooms;
    private final ReservationExtensionService extensions;
    private final AuditService audit;

    public SuperAdminRecordsController(ReservationService reservations, PaymentService payments,
            SystemUserRepository users, RoomRepository rooms,
            ReservationExtensionService extensions, AuditService audit) {
        this.reservations = reservations;
        this.payments = payments;
        this.users = users;
        this.rooms = rooms;
        this.extensions = extensions;
        this.audit = audit;
    }

    @GetMapping("/super-admin/records")
    public String allSystemRecords(Model model) {
        model.addAttribute("accounts", users.findAll());
        model.addAttribute("rooms", rooms.findAllByOrderByRoomCodeAsc());
        model.addAttribute("reservations", reservations.allReservations());
        model.addAttribute("payments", payments.allPayments());
        model.addAttribute("extensionRequests", extensions.allRequests());
        model.addAttribute("auditEvents", audit.recentEvents());
        return "super-admin/records";
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