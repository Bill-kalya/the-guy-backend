package com.theguy.app.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class RevenueTrendDTO {
    private LocalDate date;
    private Double gmv;
    private Double revenue;
    private Double payouts;
}
