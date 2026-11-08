package com.hotel.system.security;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="login_attempts")
public class LoginAttempt{
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false,unique=true,length=80)
    private String username;
    @Column(name="failed_count",nullable=false)
    private int failedCount;
    @Column(name="window_started_at",nullable=false)
    private Instant windowStartedAt;
    @Column(name="locked_until")
    private Instant lockedUntil;
    protected LoginAttempt(){}
    public LoginAttempt(String username,Instant now){this.username=username;this.windowStartedAt=now;}
    public String getUsername(){return username;}
    public int getFailedCount(){return failedCount;}
    public Instant getLockedUntil(){return lockedUntil;}
    public boolean isLocked(Instant now){return lockedUntil!=null&&lockedUntil.isAfter(now);}
    public void fail(Instant now){
        if(windowStartedAt.plusSeconds(900).isBefore(now)){failedCount=0;windowStartedAt=now;lockedUntil=null;}
        failedCount++;
        if(failedCount>=5)lockedUntil=now.plusSeconds(900);
    }
    public void clear(Instant now){failedCount=0;windowStartedAt=now;lockedUntil=null;}
}