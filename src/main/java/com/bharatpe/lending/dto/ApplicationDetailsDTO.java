package com.bharatpe.lending.dto;

import com.bharatpe.lending.enums.ApplicationStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDetailsDTO {
    @JsonProperty("externalLoadId")
    private String externalLoadId;
    @JsonProperty("agreementDate")
    private Date agreementDate;
    @JsonProperty("loanAmount")
    private Double loanAmount;
    @JsonProperty("status")
    private String status;
    @JsonProperty("decisioningDate")
    private Date decisioningDate;
    @JsonProperty("reason")
    private String reason;
    @JsonProperty("interestRate")
    private Double interestRate;
    @JsonProperty("tenure")
    private String tenure;
    @JsonProperty("arrangerFee")
    private SupportLoanResponseDTO.LoanArrangerFee arrangerFee;
    @JsonProperty("sendToNbfc")
    private String sendToNbfc;
    @JsonProperty("lender")
    private String lender;
    @JsonProperty("isInsured")
    private Boolean isInsured;
    @JsonProperty("insurancePremium")
    private Double insurancePremium;
}
