package com.theguy.app.entity;

import com.theguy.app.enums.VerificationLevel;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.List;

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
    private LocalDateTime lastActiveAt;
    
    private double ratingAvg;
    private int totalReviews;
    private int jobsCompleted;
    private int jobsCancelled;
    private double responseRate;
    private double repeatClientsPercentage;
    private double dynamicPriceMultiplier;

    @OneToMany(mappedBy = "provider", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<com.theguy.app.entity.Service> services;
    
    @Version
    private Integer version;
}