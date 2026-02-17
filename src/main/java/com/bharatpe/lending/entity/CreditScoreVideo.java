package com.bharatpe.lending.entity;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "credit_score_videos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditScoreVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private String merchantId;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Column(name = "template_id")
    private String templateId;

    @Column(name = "video_id")
    private String videoId;

    @Column(name = "video_url", nullable = false, length = 500)
    private String videoUrl;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "PENDING";

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private ScoreCategory category;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ScoreCategory {
        GOOD_SCORE,
        BAD_SCORE
    }
}

