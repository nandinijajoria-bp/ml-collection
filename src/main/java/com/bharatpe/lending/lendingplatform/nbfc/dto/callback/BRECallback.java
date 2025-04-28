package com.bharatpe.lending.lendingplatform.nbfc.dto.callback;

import com.bharatpe.lending.lendingplatform.nbfc.enums.BRERiskDecision;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BRECallback {
    private String status;
    private boolean success;
    private boolean retryable;
    private String leadId;
    private String clientId;
    private String asyncId;
    private BRERiskDecision riskDecision;
    private BigDecimal approvedLoanAmount;
    private BigDecimal lenderROI;
    private long lenderTenure;
    private String offerId;
    private String offerStatus;
}
