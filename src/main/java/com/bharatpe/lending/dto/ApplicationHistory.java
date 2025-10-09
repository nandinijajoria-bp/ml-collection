package com.bharatpe.lending.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationHistory {
    private String externalLoadId;
    private Double loanAmount;
    private String status;
    private String reason;
    private Double interestRate;
    private String tenure;
    private ArrangerFee arrangerFee;
    private String sendToNbfc;
    private String lender;
    private Double insurancePremium;
    private String agreementDate;
    private String decisioningDate;
}