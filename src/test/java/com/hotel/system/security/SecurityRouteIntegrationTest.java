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
    private RoomRepository rooms;

    @Autowired
    private RoomPhotoRepository roomPhotos;

    @Autowired
    private ReservationRepository reservations;

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
        mvc.perform(get("/admin/reports"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/reports/index"));
    }

    @Test
    @WithMockUser(username = "booking-admin", roles = "ADMIN")
    void adminHotelSettingsPageRenders() throws Exception {
        mvc.perform(get("/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/settings"));
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
