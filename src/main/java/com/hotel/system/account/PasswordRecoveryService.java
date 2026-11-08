package com.hotel.system.account;

import com.hotel.system.audit.AuditService;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
@Transactional
public class PasswordRecoveryService{
    private final SystemUserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final JavaMailSender mail;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final AuditService audit;
    private final String fromAddress;
    private final SecureRandom random=new SecureRandom();
    public PasswordRecoveryService(SystemUserRepository users,PasswordResetTokenRepository tokens,
        JavaMailSender mail,PasswordEncoder encoder,Clock clock,AuditService audit,
        @Value("${spring.mail.username:}") String fromAddress){
        this.users=users;this.tokens=tokens;this.mail=mail;this.encoder=encoder;this.clock=clock;
        this.audit=audit;this.fromAddress=fromAddress;
    }
    public void request(String email,String baseUrl){
        if(email==null||email.isBlank())return;
        users.findByEmailIgnoreCase(email.trim()).ifPresent(user->{
            tokens.deleteByUserId(user.getId());
            byte[] bytes=new byte[32];random.nextBytes(bytes);
            String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            tokens.save(new PasswordResetToken(user.getId(),hash(token),Instant.now(clock).plus(Duration.ofMinutes(20))));
            try{
                if(fromAddress==null||fromAddress.isBlank())throw new IllegalStateException("Mail is not configured.");
                SimpleMailMessage message=new SimpleMailMessage();
                message.setFrom(fromAddress);message.setTo(user.getEmail());
                message.setSubject("[HOTEL NAME] — Password reset");
                message.setText("Use this link within 20 minutes to reset your password: "+baseUrl+"/reset-password?token="+token);
                mail.send(message);
                audit.record(user.getUsername(),"PASSWORD_RESET_REQUESTED","SYSTEM_USER",user.getId().toString(),"Password reset email sent.");
            }catch(RuntimeException ex){
                tokens.deleteByUserId(user.getId());
                throw new IllegalStateException("Password recovery email could not be sent.");
            }
        });
    }
    public void reset(String token,String newPassword){
        validatePassword(newPassword);
        if(token==null||token.isBlank())throw new IllegalArgumentException("Reset link is invalid or expired.");
        PasswordResetToken record=tokens.findByTokenHash(hash(token)).orElseThrow(()->new IllegalArgumentException("Reset link is invalid or expired."));
        Instant now=Instant.now(clock);
        if(!record.isValid(now))throw new IllegalArgumentException("Reset link is invalid or expired.");
        SystemUser user=users.findById(record.getUserId()).orElseThrow(()->new IllegalArgumentException("Account not found."));
        user.setPasswordHash(encoder.encode(newPassword));
        record.markUsed(now);
        audit.record(user.getUsername(),"PASSWORD_RESET_COMPLETED","SYSTEM_USER",user.getId().toString(),"Password reset completed.");
    }
    private String hash(String value){
        try{return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception ex){throw new IllegalStateException("Secure token hashing is unavailable.",ex);}
    }
    private void validatePassword(String password){
        if(password==null||password.length()<12||!password.matches(".*[A-Z].*")||!password.matches(".*[a-z].*")
            ||!password.matches(".*\\d.*")||!password.matches(".*[^A-Za-z0-9].*"))
            throw new IllegalArgumentException("Password must be at least 12 characters and include uppercase, lowercase, a number, and a special character.");
    }
}