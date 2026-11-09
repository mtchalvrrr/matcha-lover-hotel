package com.hotel.system.security;

import com.hotel.system.account.AccountRole;
import com.hotel.system.account.SystemUser;
import com.hotel.system.account.SystemUserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:./target/portal-session-isolation-test.db",
        "hotel.storage.location=./target/portal-session-isolation-uploads",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.mail.host=",
        "captcha.enabled=false"
})
@AutoConfigureMockMvc
class PortalSessionIsolationIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private SystemUserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void threePortalsKeepSeparateSessionsAndLogoutDoesNotEndOtherSessions() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String password = "MatchaSecure!2026";
        String customerName = "customer-" + suffix;
        String adminName = "admin-" + suffix;
        String superAdminName = "superadmin-" + suffix;
        users.save(new SystemUser(customerName, customerName + "@example.test",
                passwordEncoder.encode(password), AccountRole.CUSTOMER));
        users.save(new SystemUser(adminName, adminName + "@example.test",
                passwordEncoder.encode(password), AccountRole.ADMIN));
        users.save(new SystemUser(superAdminName, superAdminName + "@example.test",
                passwordEncoder.encode(password), AccountRole.SUPER_ADMIN));

        String customerSession = login("/customer/authenticate", customerName, password, "customer",
                PortalSessionIdResolver.CUSTOMER_COOKIE);
        String adminSession = login("/admin/authenticate", adminName, password, "admin",
                PortalSessionIdResolver.ADMIN_COOKIE);
        String superAdminSession = login("/super-admin/authenticate", superAdminName, password, "super-admin",
                PortalSessionIdResolver.SUPER_ADMIN_COOKIE);

        Cookie[] allPortalCookies = {
                new Cookie(PortalSessionIdResolver.CUSTOMER_COOKIE, customerSession),
                new Cookie(PortalSessionIdResolver.ADMIN_COOKIE, adminSession),
                new Cookie(PortalSessionIdResolver.SUPER_ADMIN_COOKIE, superAdminSession)
        };

        mvc.perform(get("/customer/dashboard").cookie(allPortalCookies))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/dashboard").cookie(allPortalCookies))
                .andExpect(status().isOk());
        mvc.perform(get("/super-admin/dashboard").cookie(allPortalCookies))
                .andExpect(status().isOk());

        MvcResult adminLogout = mvc.perform(post("/admin/logout").cookie(allPortalCookies).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertTrue(adminLogout.getResponse().getHeaders("Set-Cookie").stream()
                .anyMatch(value -> value.startsWith(PortalSessionIdResolver.ADMIN_COOKIE + "=")
                        && value.contains("Max-Age=0")));

        mvc.perform(get("/customer/dashboard").cookie(
                        new Cookie(PortalSessionIdResolver.CUSTOMER_COOKIE, customerSession)))
                .andExpect(status().isOk());
        mvc.perform(get("/super-admin/dashboard").cookie(
                        new Cookie(PortalSessionIdResolver.SUPER_ADMIN_COOKIE, superAdminSession)))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/dashboard").cookie(
                        new Cookie(PortalSessionIdResolver.ADMIN_COOKIE, adminSession)))
                .andExpect(status().is3xxRedirection());
    }

    private String login(String endpoint, String username, String password,
                         String portal, String cookieName) throws Exception {
        MvcResult result = mvc.perform(post(endpoint).with(csrf())
                        .param("username", username)
                        .param("password", password)
                        .param("portal", portal))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertNotNull(result.getResponse().getRedirectedUrl());
        assertTrue(result.getResponse().getRedirectedUrl().endsWith("/" + portal + "/dashboard"),
                "Login should reach the authenticated portal dashboard.");
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith(cookieName + "="))
                .map(value -> value.substring(cookieName.length() + 1, value.indexOf(';')))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing isolated session cookie: " + cookieName));
    }
}
