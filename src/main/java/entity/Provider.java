package com.theguy.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "providers")
@Data
@EqualsAndHashCode(callSuper = true)
public class Provider extends BaseEntity {
    @OneToOne
    private User user;
    
    private String bio;
    private String profileImageUrl;
    
    @Enumerated(EnumType.STRING)
    private VerificationLevel verificationLevel;
    
    private boolean isOnline;
    private java.time.LocalDateTime lastActiveAt;
    
    private double ratingAvg;
    private int totalReviews;
    private int jobsCompleted;
    private int jobsCancelled;
    private double responseRate;
    private double repeatClientsPercentage;
    private double dynamicPriceMultiplier;
    
    public enum VerificationLevel {
        NONE, BASIC, ID_VERIFIED, BUSINESS
    }
}