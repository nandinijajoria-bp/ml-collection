package com.bharatpe.lending.loanV3.revamp.dto;

import lombok.*;

@Data
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LenderEvaluationStateDTO {
    private Long applicationId;
    private Boolean isRetryable;
    private boolean isTopup;
    private String lender;
    private Long merchantId;
}
