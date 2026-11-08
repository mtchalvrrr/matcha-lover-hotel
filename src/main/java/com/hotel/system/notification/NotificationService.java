package com.hotel.system.notification;

import com.hotel.system.account.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional
public class NotificationService{
    private final NotificationRepository notifications;
    private final SystemUserRepository users;
    public NotificationService(NotificationRepository notifications,SystemUserRepository users){
        this.notifications=notifications;this.users=users;
    }
    public void notifyUser(String username,String title,String message,String category){
        if(username!=null&&!username.isBlank())
            notifications.save(new Notification(username,title,message,category));
    }
    public void notifyAdmins(String title,String message,String category){
        users.findByRoleInOrderByCreatedAtDesc(List.of(AccountRole.ADMIN))
            .forEach(admin->notifyUser(admin.getUsername(),title,message,category));
    }
    public void notifySuperAdmins(String title,String message,String category){
        users.findByRoleInOrderByCreatedAtDesc(List.of(AccountRole.SUPER_ADMIN))
            .forEach(admin->notifyUser(admin.getUsername(),title,message,category));
    }
    @Transactional(readOnly=true)
    public List<Notification> inbox(String username){
        return notifications.findByRecipientUsernameOrderByCreatedAtDesc(username);
    }
    public void markRead(Long id,String username){
        Notification notification=notifications.findByIdAndRecipientUsername(id,username)
            .orElseThrow(()->new IllegalArgumentException("Notification not found."));
        notification.markRead();
    }
}