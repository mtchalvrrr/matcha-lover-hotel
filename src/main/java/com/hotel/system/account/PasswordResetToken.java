package com.hotel.system.account;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="password_reset_tokens",indexes=@Index(name="idx_reset_token_hash",columnList="token_hash",unique=true))
public class PasswordResetToken{
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="user_id",nullable=false)
    private Long userId;
    @Column(name="token_hash",nullable=false,unique=true,length=64)
    private String tokenHash;
    @Column(name="expires_at",nullable=false)
    private Instant expiresAt;
    @Column(name="used_at")
    private Instant usedAt;
    protected PasswordResetToken(){}
    public PasswordResetToken(Long userId,String tokenHash,Instant expiresAt){this.userId=userId;this.tokenHash=tokenHash;this.expiresAt=expiresAt;}
    public Long getId(){return id;}
    public Long getUserId(){return userId;}
    public String getTokenHash(){return tokenHash;}
    public Instant getExpiresAt(){return expiresAt;}
    public Instant getUsedAt(){return usedAt;}
    public boolean isValid(Instant now){return usedAt==null&&expiresAt.isAfter(now);}
    public void markUsed(Instant now){usedAt=now;}
}