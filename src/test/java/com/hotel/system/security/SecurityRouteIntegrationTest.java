package com.hotel.system.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

    @Test
    void anonymousVisitorsCanBrowseRooms() throws Exception {
        mvc.perform(get("/rooms"))
                .andExpect(status().isOk())
                .andExpect(view().name("rooms/list"));
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
