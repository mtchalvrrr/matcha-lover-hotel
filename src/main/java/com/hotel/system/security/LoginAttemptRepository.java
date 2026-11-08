package com.hotel.system.security;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt,Long>{
    Optional<LoginAttempt> findByUsernameIgnoreCase(String username);
}