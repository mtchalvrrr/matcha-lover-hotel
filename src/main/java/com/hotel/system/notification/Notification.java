package com.hotel.system.notification;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="user_notifications", indexes={
    @Index(name="idx_notification_recipient",columnList="recipient_username,created_at"),
    @Index(name="idx_notification_read",columnList="read_at")
})
public class Notification {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="recipient_username",nullable=false,length=80)
    private String recipientUsername;
    @Column(nullable=false,length=120)
    private String title;
    @Column(nullable=false,length=1000)
    private String message;
    @Column(nullable=false,length=40)
    private String category;
    @Column(name="event_key", length=180, unique=true)
    private String eventKey;
    @Column(name="created_at",nullable=false,updatable=false)
    private Instant createdAt=Instant.now();
    @Column(name="read_at")
    private Instant readAt;
    protected Notification(){}
    public Notification(String recipient,String title,String message,String category){
        this(recipient, title, message, category, null);
    }
    public Notification(String recipient,String title,String message,String category,String eventKey){
        this.recipientUsername=recipient;this.title=title;this.message=message;this.category=category;
        this.eventKey=eventKey;
    }
    public Long getId(){return id;}
    public String getRecipientUsername(){return recipientUsername;}
    public String getTitle(){return title;}
    public String getMessage(){return message;}
    public String getCategory(){return category;}
    public String getEventKey(){return eventKey;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getReadAt(){return readAt;}
    public boolean isRead(){return readAt!=null;}
    public void markRead(){if(readAt==null)readAt=Instant.now();}
}