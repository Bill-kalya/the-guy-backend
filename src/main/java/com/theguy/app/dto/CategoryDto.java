package com.theguy.app.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CategoryDto {
    private UUID id;
    private String name;
    private String description;
    private String iconUrl;
    private int sortOrder;
    private UUID parentId;
    private boolean isActive;
}