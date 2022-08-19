package com.bharatpe.lending.dto;

import com.bharatpe.lending.enums.ApplicationStatus;
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

    private String externalLoadId;
    private Date agreementDate;
    private Double loanAmount;
    private String status;
    private Date decisioningDate;
    private String reason;
    private Double interestRate;
    private String tenure;
    private SupportLoanResponseDTO.LoanArrangerFee arrangerFee;
}
