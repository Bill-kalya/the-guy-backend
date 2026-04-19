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
    private String phoneNumber;
    
    private String email;
    private String passwordHash;
    
    @Enumerated(EnumType.STRING)
    private Role role;
    
    private boolean isVerified;
}