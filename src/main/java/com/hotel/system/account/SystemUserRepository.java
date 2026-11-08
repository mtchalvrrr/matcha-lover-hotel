package com.hotel.system.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface SystemUserRepository extends JpaRepository<SystemUser, Long> {
    long countByRole(AccountRole role);
    Optional<SystemUser> findByUsernameIgnoreCase(String username);
    Optional<SystemUser> findByEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    long countByRole(AccountRole role);
    List<SystemUser> findByRoleInOrderByCreatedAtDesc(Collection<AccountRole> roles);
}
