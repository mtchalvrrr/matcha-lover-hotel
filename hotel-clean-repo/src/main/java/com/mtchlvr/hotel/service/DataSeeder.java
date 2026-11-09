package com.mtchlvr.hotel.service;

import com.mtchlvr.hotel.model.*;
import com.mtchlvr.hotel.repo.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import java.math.BigDecimal;

@Configuration
public class DataSeeder {
    @Bean
    CommandLineRunner seed(
            RoomRepository rooms,
            UserRepository users,
            PasswordEncoder encoder,
            @Value("${HOTEL_MANAGER_EMAIL:}") String managerEmail,
            @Value("${HOTEL_MANAGER_PASSWORD:}") String managerPassword,
            @Value("${HOTEL_MANAGER_NAME:Hotel Manager}") String managerName) {
        return args -> {
            // Seed each missing room independently so a partially populated database is repaired.
            addType(rooms, "Standard", "1", new BigDecimal("1800"), 2);
            addType(rooms, "Deluxe", "2", new BigDecimal("2600"), 2);
            addType(rooms, "Suite", "3", new BigDecimal("4200"), 2);
            addType(rooms, "Family", "4", new BigDecimal("3500"), 4);
            // Never ship a known manager email/password in source control.
            // Provision the first manager only when the operator supplies both secrets.
            String normalizedManagerEmail = managerEmail == null
                    ? "" : managerEmail.trim().toLowerCase(java.util.Locale.ROOT);
            if (!normalizedManagerEmail.isBlank() &&
                    managerPassword != null && !managerPassword.isBlank()) {
                AppUser manager = users.findByEmailIgnoreCase(normalizedManagerEmail).orElse(null);
                if (manager == null) {
                    manager = new AppUser(normalizedManagerEmail,
                        encoder.encode(managerPassword),
                        managerName == null || managerName.isBlank() ? "Hotel Manager" : managerName.trim(),
                        "MANAGER", true);
                } else {
                    if (!"MANAGER".equalsIgnoreCase(manager.getRole())) {
                        throw new IllegalStateException(
                            "HOTEL_MANAGER_EMAIL belongs to a non-manager account. Choose a separate manager email."
                        );
                    }
                    // Re-provision the configured manager on startup so a previously
                    // exposed demo password is replaced in the existing local database.
                    manager.setPasswordHash(encoder.encode(managerPassword));
                }
                users.save(manager);
            }
        };
    }
    private void addType(RoomRepository rooms, String type, String prefix, BigDecimal rate, int capacity) {
        for (int i = 1; i <= 3; i++) {
            String number = prefix + "0" + i;
            if (!rooms.existsByRoomNumber(number)) {
                rooms.save(new Room(number, type, rate, capacity));
            }
        }
    }
}
