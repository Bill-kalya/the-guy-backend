package com.theguy.app.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RiskOverviewDTO {
    private Long low;
    private Long medium;
    private Long high;
    private Long critical;
}
