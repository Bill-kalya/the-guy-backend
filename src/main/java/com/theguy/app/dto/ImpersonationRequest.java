package com.theguy.app.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class ImpersonationRequest {
    private UUID userId;
}
