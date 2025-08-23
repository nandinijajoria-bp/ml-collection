package com.bharatpe.lending.ai.dto;

import com.bharatpe.lending.loanV2.dto.Eligibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoanDetailResponse {
    private LoanApplicationDetail currentLoan;
    private Eligibility eligibility;
    private Object stageDetail;

}
