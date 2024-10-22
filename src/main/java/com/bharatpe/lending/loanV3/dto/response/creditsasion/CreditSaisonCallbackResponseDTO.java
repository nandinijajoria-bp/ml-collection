package com.bharatpe.lending.loanV3.dto.response.creditsasion;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreditSaisonCallbackResponseDTO {
    private String partnerLoanId;
    private String status;
    private String message;
    private String reason;
    private String appFormId;
    private String utr;
    private String po;
    private String disbursalDate;
    private Double disbursalAmount;
    private String ksfLoanId;
}
