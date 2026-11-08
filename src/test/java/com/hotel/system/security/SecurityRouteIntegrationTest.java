package com.hotel.system.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import com.hotel.system.room.Room;
import com.hotel.system.room.RoomRepository;
import com.hotel.system.room.RoomPhoto;
import com.hotel.system.room.RoomPhotoRepository;
import com.hotel.system.reservation.Reservation;
import com.hotel.system.reservation.ReservationRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.mock.web.MockHttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.ServletContext;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.is;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:./target/security-route-test.db",
        "hotel.storage.location=./target/security-route-uploads",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.mail.host="
})
@AutoConfigureMockMvc
class SecurityRouteIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private SessionRegistry sessionRegistry;

    @Autowired
    private HttpSessionEventPublisher sessionEventPublisher;

    @Autowired
    private ServletContext servletContext;

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private RoomPhotoRepository roomPhotos;

    @Autowired
    private ReservationRepository reservations;


    @Test
    void destroyedHttpSessionsAreRemovedFromConcurrentSessionRegistry() {
        var principal = User.withUsername("session-cleanup-user")
                .password("not-used")
                .roles("ADMIN")
                .build();
        MockHttpSession session = new MockHttpSession(servletContext);
        sessionRegistry.registerNewSession(session.getId(), principal);

        assertEquals(1, sessionRegistry.getAllSessions(principal, false).size());

        sessionEventPublisher.sessionDestroyed(new HttpSessionEvent(session));

        assertEquals(0, sessionRegistry.getAllSessions(principal, false).size());
    }

    @Test
    void responsesIncludeSecurityHeaders() throws Exception {
        mvc.perform(get("/rooms"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"));
    }

    @Test
    @WithMockUser(username = "private-page-user", roles = "CUSTOMER")
    void authenticatedPortalPagesAreNotCachedByTheBrowser() throws Exception {
        mvc.perform(get("/customer/dashboard"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void anonymousVisitorsCanBrowseRooms() throws Exception {
        mvc.perform(get("/rooms"))
                .andExpect(status().isOk())
                .andExpect(view().name("rooms/list"));
    }

    @Test
    void roomDetailsRenderWithUploadedPhotoGallery() throws Exception {
        Room room = new Room();
        room.setRoomCode("TEST-01");
        room.setName("Test Garden Room");
        room.setRoomType("Deluxe");
        room.setDescription("A test room for template verification.");
        room.setCapacity(2);
        room.setNightlyRate(new BigDecimal("1500.00"));
        room.setAmenities("Wi-Fi, Air conditioning");
        Room saved = rooms.save(room);
        roomPhotos.save(new RoomPhoto(saved.getId(), "test-room-photo.jpg", "test-room-photo.jpg", true));

        mvc.perform(get("/rooms/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("rooms/detail"));
    }

    @Test
    @WithMockUser(username = "booking-admin", roles = "ADMIN")
    void adminReservationSearchRendersFilteredResults() throws Exception {
        Reservation reservation = reservations.save(new Reservation(null, "searchable-guest",
                "searchable@example.com", 1L, "SEARCH-01", "Search Room",
                java.time.LocalDate.of(2026, 12, 1), java.time.LocalDate.of(2026, 12, 3),
                2, 2, new BigDecimal("1000.00"), new BigDecimal("2000.00")));

        mvc.perform(get("/admin/reservations").param("q", reservation.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/reservations/list"));
    }

    @Test
    @WithMockUser(username = "booking-admin", roles = "ADMIN")
    void adminReportsRenderWithDailyTrendAndCapacityMetrics() throws Exception {
        reservations.save(new Reservation(null, "report-guest", "report@example.com", 1L,
                "REPORT-01", "Report Room", java.time.LocalDate.of(2026, 12, 1),
                java.time.LocalDate.of(2026, 12, 2), 2, 1,
                new BigDecimal("1000.00"), new BigDecimal("1000.00")));
        mvc.perform(get("/admin/reports").param("from", "2026-12-01").param("to", "2026-12-02"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/reports/index"))
                .andExpect(model().attributeExists("dailyRequestTotal"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Reservations with check-in dates in selected period")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Approval rate")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Peak booking day")));
    }

    @Test
    @WithMockUser(username = "booking-admin", roles = "ADMIN")
    void adminReportCanBeExportedAsCsv() throws Exception {
        mvc.perform(get("/admin/reports/export.csv")
                        .param("from", "2026-12-01")
                        .param("to", "2026-12-02"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Reservations by room")));
    }

    @Test
    @WithMockUser(username = "booking-admin", roles = "ADMIN")
    void adminReportCanBeExportedAsPdf() throws Exception {
        mvc.perform(get("/admin/reports/export.pdf")
                        .param("from", "2026-12-01")
                        .param("to", "2026-12-02"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/pdf"))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertTrue(
                        result.getResponse().getContentAsByteArray().length > 100));
    }

    @Test
    @WithMockUser(username = "booking-admin", roles = "ADMIN")
    void adminHotelSettingsPageRenders() throws Exception {
        mvc.perform(get("/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/settings"));
    }

    @Test
    void publicAvailabilitySearchExcludesRoomsWithOverlappingReservations() throws Exception {
        Room room = new Room();
        room.setRoomCode("BUSY-01");
        room.setName("Busy Room");
        room.setRoomType("Standard");
        room.setDescription("Room reserved for the requested dates.");
        room.setCapacity(2);
        room.setNightlyRate(new BigDecimal("1200.00"));
        room.setAmenities("Wi-Fi");
        Room saved = rooms.save(room);
        reservations.save(new Reservation(null, "existing-guest", "existing@example.com", saved.getId(),
                saved.getRoomCode(), saved.getName(), java.time.LocalDate.of(2026, 12, 10),
                java.time.LocalDate.of(2026, 12, 12), 2, 2,
                new BigDecimal("1200.00"), new BigDecimal("2400.00")));

        mvc.perform(get("/rooms").param("checkIn", "2026-12-10")
                        .param("checkOut", "2026-12-12").param("guests", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Busy Room"))));

        mvc.perform(get("/rooms/{id}", saved.getId()).param("checkIn", "2026-12-10")
                        .param("checkOut", "2026-12-12").param("guests", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "This room is not available")));
    }

    @Test
    @WithMockUser(username = "history-guest", roles = "CUSTOMER")
    void customerCanViewOnlyTheirOwnReservationHistory() throws Exception {
        Reservation reservation = reservations.save(new Reservation(null, "history-guest",
                "history@example.com", 1L, "HIST-01", "History Room",
                java.time.LocalDate.of(2026, 12, 20), java.time.LocalDate.of(2026, 12, 22),
                2, 2, new BigDecimal("1000.00"), new BigDecimal("2000.00")));

        mvc.perform(get("/customer/reservations/{id}/history", reservation.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("reservations/history"));

        Reservation anotherCustomer = reservations.save(new Reservation(null, "different-guest",
                "different@example.com", 1L, "OTHER-01", "Other Room",
                java.time.LocalDate.of(2026, 12, 23), java.time.LocalDate.of(2026, 12, 24),
                1, 1, new BigDecimal("1000.00"), new BigDecimal("1000.00")));
        mvc.perform(get("/customer/reservations/{id}/history", anotherCustomer.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/reservations"));
    }

    @Test
    void publicHotelChatCanRedirectGuestsToRoomCatalog() throws Exception {
        mvc.perform(post("/api/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Show me the rooms\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectUrl", is("/rooms")));
    }

    @Test
    @WithMockUser(username = "booking-admin", roles = "ADMIN")
    void adminDashboardRendersLiveMetrics() throws Exception {
        mvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboards/admin-dashboard"));
    }

    @Test
    @WithMockUser(username = "guest", roles = "CUSTOMER")
    void customerDashboardRendersLiveReservationSummary() throws Exception {
        mvc.perform(get("/customer/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboards/customer-dashboard"));
    }

    @Test
    @WithMockUser(username = "system-superadmin", roles = "SUPER_ADMIN")
    void superAdminDashboardRendersLiveApprovalAndAuditMetrics() throws Exception {
        mvc.perform(get("/super-admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboards/super-admin-dashboard"));
    }

    @Test
    void customerPrivateRoutesRequireCustomerAuthentication() throws Exception {
        mvc.perform(get("/customer/reservations"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/customer/login"));
    }

    @Test
    void adminPrivateRoutesRequireAdminAuthentication() throws Exception {
        mvc.perform(get("/admin/reservations"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/admin/login"));
    }

    @Test
    void superAdminPrivateRoutesRequireSuperAdminAuthentication() throws Exception {
        mvc.perform(get("/super-admin/audit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/super-admin/login"));
    }
}
