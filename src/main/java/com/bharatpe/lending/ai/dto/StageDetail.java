package com.bharatpe.lending.ai.dto;

import com.bharatpe.lending.ai.dto.stageDetailResponse.KfsStageDetail;
import com.bharatpe.lending.ai.dto.stageDetailResponse.KycStageDetail;
import com.bharatpe.lending.ai.dto.stageDetailResponse.LenderEvaluationStageDetail;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StageDetail {
    private LenderEvaluationStageDetail lenderEvaluationStageDetail;
    private Object nachStageDetail;
    private KfsStageDetail kfsStageDetail;
    private KycStageDetail kycStageDetail;
    private LedgerApiResponse ledgerApiResponse;
    private Object shopPictureStageDetails;
}
