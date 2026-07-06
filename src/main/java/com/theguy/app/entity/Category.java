package com.theguy.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Entity
@Table(name = "categories")
@Data
@EqualsAndHashCode(callSuper = true)
public class Category extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    private String iconUrl;

    @Column(nullable = false)
    private int sortOrder;

    private UUID parentId;

    private boolean isActive = true;
}