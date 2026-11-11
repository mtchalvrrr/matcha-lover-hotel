package com.hotel.system.payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentProviderSettingRepository extends JpaRepository<PaymentProviderSetting, Long> { }