package com.hotel.system.reservation;

import com.hotel.system.payment.XenditService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
public class ReservationExtensionController {
    private final ReservationExtensionService extensions;
    private final ReservationService reservations;
    private final XenditService xendit;

    public ReservationExtensionController(ReservationExtensionService extensions,
            ReservationService reservations, XenditService xendit) {
        this.extensions = extensions;
        this.reservations = reservations;
        this.xendit = xendit;
    }

    /** Customer submits a checkout-date extension request for their own reservation. */
    @PostMapping("/customer/reservations/{id}/extensions")
    public String requestExtension(@PathVariable Long id,
            @RequestParam LocalDate requestedCheckoutDate,
            @RequestParam(required = false) String customerNote,
            Authentication authentication, RedirectAttributes redirect) {
        try {
            extensions.request(id, authentication.getName(), requestedCheckoutDate, customerNote);
            redirect.addFlashAttribute("success",
                    "Extension request submitted. The Admin must review it before any additional payment is made.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/customer/reservation-extensions";
    }

    /** Shows only extension requests owned by the signed-in customer. */
    @GetMapping("/customer/reservation-extensions")
    public String customerExtensions(Authentication authentication, Model model) {
        model.addAttribute("extensionRequests", extensions.customerRequests(authentication.getName()));
        model.addAttribute("reservations", reservations.customerReservations(authentication.getName()));
        return "customer/reservation-extensions";
    }

    /** Starts a full hosted checkout only after an Admin has approved the extension. */
    @PostMapping("/customer/reservation-extensions/{id}/pay")
    public String payExtension(@PathVariable Long id, Authentication authentication,
            RedirectAttributes redirect) {
        try {
            return "redirect:" + xendit.createExtensionCheckout(id, authentication.getName());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/customer/reservation-extensions";
        }
    }

    @PreAuthorize("@adminPermissionService.isEnabled('RESERVATION_EDIT')")
    @GetMapping("/admin/reservation-extensions")
    public String adminExtensions(Model model) {
        model.addAttribute("extensionRequests", extensions.allRequests());
        return "admin/reservations/extensions";
    }

    /** Admin decision is audited; rejection requires a reason. */
    @PreAuthorize("@adminPermissionService.isEnabled('RESERVATION_EDIT')")
    @PostMapping("/admin/reservation-extensions/{id}/review")
    public String reviewExtension(@PathVariable Long id, @RequestParam boolean approve,
            @RequestParam(required = false) String reason, Authentication authentication,
            RedirectAttributes redirect) {
        try {
            extensions.review(id, approve, authentication.getName(), reason);
            redirect.addFlashAttribute("success",
                    approve ? "Extension approved. The customer can now pay the full additional charge."
                            : "Extension request rejected.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/reservation-extensions";
    }
}