package com.hotel.system.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification,Long>{
    List<Notification> findByRecipientUsernameOrderByCreatedAtDesc(String username);
    Optional<Notification> findByIdAndRecipientUsername(Long id,String username);
    boolean existsByEventKey(String eventKey);
}