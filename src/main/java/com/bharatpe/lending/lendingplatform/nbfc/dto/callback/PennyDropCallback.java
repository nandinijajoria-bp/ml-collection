package com.bharatpe.lending.lendingplatform.nbfc.dto.callback;

import com.bharatpe.lending.lendingplatform.nbfc.enums.PennyDropCallbackStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
public class PennyDropCallback {
    private String status;
    private String statusCode;
    private boolean success;
    private boolean retryable;
    private String leadId;
    private String clientId;
    private String asyncId;
    private PennyDropCallbackStatus riskDecision;
    private BigDecimal approvedLoanAmount;
}
