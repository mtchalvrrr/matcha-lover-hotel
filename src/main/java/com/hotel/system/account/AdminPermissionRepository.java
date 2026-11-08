package com.hotel.system.account;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminPermissionRepository extends JpaRepository<AdminPermissionSetting, String> { }
