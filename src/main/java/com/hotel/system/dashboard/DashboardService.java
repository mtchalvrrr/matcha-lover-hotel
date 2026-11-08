package com.hotel.system.dashboard;

import com.hotel.system.account.*;
import com.hotel.system.audit.AuditLog;
import com.hotel.system.audit.AuditLogRepository;
import com.hotel.system.payment.PaymentRepository;
import com.hotel.system.payment.PaymentStatus;
import com.hotel.system.registration.RegistrationRepository;
import com.hotel.system.registration.RegistrationStatus;
import com.hotel.system.reservation.ReservationRepository;
import com.hotel.system.reservation.ReservationStatus;
import com.hotel.system.room.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DashboardService {
    private final ReservationRepository reservations;
    private final RoomRepository rooms;
    private final PaymentRepository payments;
    private final SystemUserRepository users;
    private final RegistrationRepository registrations;
    private final AccountDeactivationRequestRepository deactivations;
    private final AuditLogRepository audit;
    private final Clock clock;

    public DashboardService(ReservationRepository reservations, RoomRepository rooms,
                            PaymentRepository payments, SystemUserRepository users,
                            RegistrationRepository registrations,
                            AccountDeactivationRequestRepository deactivations,
                            AuditLogRepository audit, Clock clock) {
        this.reservations = reservations;
        this.rooms = rooms;
        this.payments = payments;
        this.users = users;
        this.registrations = registrations;
        this.deactivations = deactivations;
        this.audit = audit;
        this.clock = clock;
    }

    public AdminSummary adminSummary() {
        LocalDate today = LocalDate.now(clock);
        return new AdminSummary(
                reservations.count(),
                reservations.countByStatus(ReservationStatus.PENDING),
                reservations.countByStatus(ReservationStatus.APPROVED),
                reservations.countByStatus(ReservationStatus.CHECKED_IN),
                reservations.countByStatus(ReservationStatus.CHECKED_OUT),
                reservations.countByCheckInDateAndStatusIn(today,
                        List.of(ReservationStatus.APPROVED, ReservationStatus.CHECKED_IN)),
                reservations.countByCheckOutDateAndStatusIn(today,
                        List.of(ReservationStatus.CHECKED_IN)),
                rooms.countByActiveTrue(),
                payments.sumAmountByStatus(PaymentStatus.PAID),
                audit.findTop5ByOrderByCreatedAtDesc()
        );
    }

    public CustomerSummary customerSummary(String username) {
        return new CustomerSummary(
                reservations.countByCustomerUsernameAndStatus(username, ReservationStatus.PENDING),
                reservations.countByCustomerUsernameAndStatus(username, ReservationStatus.APPROVED),
                reservations.countByCustomerUsernameAndStatus(username, ReservationStatus.CHECKED_IN),
                reservations.countByCustomerUsernameAndStatus(username, ReservationStatus.CHECKED_OUT),
                reservations.countByCustomerUsernameAndStatusIn(username,
                        List.of(ReservationStatus.PENDING, ReservationStatus.APPROVED, ReservationStatus.CHECKED_IN))
        );
    }

    public SuperAdminSummary superAdminSummary() {
        return new SuperAdminSummary(
                registrations.countByStatus(RegistrationStatus.PENDING_REVIEW),
                deactivations.countByStatus(DeactivationRequestStatus.PENDING),
                users.countByRole(AccountRole.ADMIN),
                users.countByRole(AccountRole.CUSTOMER),
                audit.findTop5ByOrderByCreatedAtDesc()
        );
    }

    public record AdminSummary(long totalReservations, long pendingReservations,
                               long approvedReservations, long checkedInReservations,
                               long checkedOutReservations, long arrivalsToday, long departuresToday, long activeRooms,
                               BigDecimal paidRevenue, List<AuditLog> recentActivity) { }

    public record CustomerSummary(long pendingReservations, long approvedReservations,
                                  long checkedInReservations, long completedReservations,
                                  long activeReservations) { }

    public record SuperAdminSummary(long pendingRegistrations, long pendingDeactivations,
                                    long adminAccounts, long customerAccounts,
                                    List<AuditLog> recentActivity) { }
}
