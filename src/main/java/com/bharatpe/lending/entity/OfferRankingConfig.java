package com.bharatpe.lending.entity;

import com.bharatpe.lending.enums.RankingType;
import com.bharatpe.lending.enums.SortOrder;
import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "offer_ranking_config")
@Data
public class OfferRankingConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "ranking_type", nullable = false)
    private RankingType rankingType;  // INITIAL or FALLBACK

    @Column(name = "priority_order", nullable = false)
    private Integer priorityOrder; // 1 = primary, 2 = tie-breaker

    //todo : enum for field names
    @Column(name = "field_name", nullable = false)
    private String fieldName; // e.g. "utilizationRate", "successRate", "interestRate"

    @Enumerated(EnumType.STRING)
    @Column(name = "sort_order", nullable = false)
    private SortOrder sortOrder; // ASC or DESC

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "weightage")
    private Double weightage; // Optional if using weighted logic

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
