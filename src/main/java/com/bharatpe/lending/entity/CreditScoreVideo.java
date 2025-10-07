package com.bharatpe.lending.entity;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDate;

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

    @Column(name = "video_link", nullable = false, length = 500)
    private String videoLink;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private ScoreCategory category;

    @Column(name = "is_valid", nullable = false)
    private Boolean isValid;

    @Column(name = "valid_till")
    private LocalDate validTill;

    @Column(name = "video_generated_date", nullable = false)
    private LocalDate videoGeneratedDate;

    public enum ScoreCategory {
        GOOD_SCORE,
        BAD_SCORE
    }
}

