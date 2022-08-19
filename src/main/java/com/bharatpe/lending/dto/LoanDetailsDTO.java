package com.bharatpe.lending.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoanDetailsDTO {

    private String externalLoanId;
    private Double loanAmount;
    private String tenure;
    private Date disbursalDate;
    private Double interestRate;
    private Double ediAmount;
    private Integer remainingEdiCount;
    private Date nextEdiDate;
    private Double paidAmount;
    private Date tentativeClosingDate;
    private Double repayment;
    private String status;
    private SupportLoanResponseDTO.LoanArrangerFee loanArrangerFee;
    private Double processingFee;
    private List<Map<String, Object>> lendingLedger;
    private String arrangerFeeStatus;
    private String nocUrl;
    private Double forClosureAmount;

}
