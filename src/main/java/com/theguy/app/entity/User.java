package com.theguy.app.entity;

import com.theguy.app.enums.Role;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "users")
@Data
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {
    private String fullName;
    
    @Column(unique = true)
    private String email;
    
    private String phoneNumber;
    
    private String passwordHash;
    
    @Enumerated(EnumType.STRING)
    private Role role;
    
    private boolean isVerified;
    
    private String verificationToken;

    private String avatarUrl;

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
    
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}
