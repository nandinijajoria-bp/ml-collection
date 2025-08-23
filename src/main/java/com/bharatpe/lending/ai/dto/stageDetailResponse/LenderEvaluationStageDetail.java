package com.bharatpe.lending.ai.dto.stageDetailResponse;

import lombok.Data;

@Data
public class LenderEvaluationStageDetail{
    private String status;
    private String kycStatus;
    private String breStatus;
    private String loanStatus;
}
