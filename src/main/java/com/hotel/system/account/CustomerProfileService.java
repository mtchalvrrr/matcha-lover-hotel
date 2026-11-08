package com.hotel.system.account;

import com.hotel.system.audit.AuditService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.regex.Pattern;

@Service
@Transactional
public class CustomerProfileService {
    private final SystemUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    public CustomerProfileService(SystemUserRepository users,PasswordEncoder encoder,AuditService audit){
        this.users=users;this.encoder=encoder;this.audit=audit;
    }
    @Transactional(readOnly=true)
    public SystemUser profile(String username){return users.findByUsernameIgnoreCase(username)
        .orElseThrow(()->new IllegalArgumentException("Customer account not found."));}
    public void update(String username,String firstName,String lastName,String email){
        SystemUser user=profile(username);
        String first=clean(firstName),last=clean(lastName),address=clean(email).toLowerCase();
        if(first.isBlank()||last.isBlank()||first.length()>80||last.length()>80)
            throw new IllegalArgumentException("Enter a valid first and last name.");
        if(address.length()>254||!Pattern.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$",address))
            throw new IllegalArgumentException("Enter a valid email address.");
        if(users.existsByEmailIgnoreCaseAndIdNot(address,user.getId()))
            throw new IllegalArgumentException("Email is already used by another account.");
        user.updateProfile(first,last,address);
        audit.record(username,"CUSTOMER_PROFILE_UPDATED","SYSTEM_USER",user.getId().toString(),"Updated profile information.");
    }
    public void changePassword(String username,String currentPassword,String newPassword){
        SystemUser user=profile(username);
        if(currentPassword==null||!encoder.matches(currentPassword,user.getPasswordHash()))
            throw new IllegalArgumentException("Current password is incorrect.");
        if(newPassword==null||newPassword.length()<12||!newPassword.matches(".*[A-Z].*")
            ||!newPassword.matches(".*[a-z].*")||!newPassword.matches(".*\\d.*")
            ||!newPassword.matches(".*[^A-Za-z0-9].*"))
            throw new IllegalArgumentException("New password must be at least 12 characters and include uppercase, lowercase, a number, and a special character.");
        if(encoder.matches(newPassword,user.getPasswordHash()))
            throw new IllegalArgumentException("Choose a password you have not used before.");
        user.setPasswordHash(encoder.encode(newPassword));
        audit.record(username,"CUSTOMER_PASSWORD_CHANGED","SYSTEM_USER",user.getId().toString(),"Customer changed their password.");
    }
    private String clean(String value){return value==null?"":value.trim();}
}