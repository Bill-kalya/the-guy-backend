package com.theguy.app.entity;

import com.theguy.app.enums.DisputeStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "disputes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Dispute extends BaseEntity {

    @OneToOne
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne
    @JoinColumn(name = "opened_by_id", nullable = false)
    private User openedBy;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @ElementCollection
    @CollectionTable(name = "dispute_evidence_urls", joinColumns = @JoinColumn(name = "dispute_id"))
    @Column(name = "evidence_url")
    private List<String> evidenceUrls;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DisputeStatus status = DisputeStatus.OPEN;

    @Column
    private Double refundAmount;

    @Column
    private Double providerPenalty;

    @Column(columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column
    private LocalDateTime resolvedAt;

    @Version
    private Integer version;
}
