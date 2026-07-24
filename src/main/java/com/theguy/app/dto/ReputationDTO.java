package com.theguy.app.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReputationDTO {
    private Integer score;
    private String tier;
    private Double sqsContribution;
    private Double completionContribution;
    private Double responseContribution;
    private Double consistencyBonus;
    private Double cancellationPenalty;
    private Double disputePenalty;
}
