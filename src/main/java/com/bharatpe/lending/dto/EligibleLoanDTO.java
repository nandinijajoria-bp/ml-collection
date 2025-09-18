package com.bharatpe.lending.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibleLoanDTO {
    private Long merchantId;
    private Long experianId;
    private Double amount;
    private String tenure;
    private String status;
    private String category;
    private Integer ioEdiDays;
    private Integer ediFreeDays;
    private Double avgTpv;
    private Integer edi;
    private Integer ioEdi;
    private Integer repayment;
    private String loanConstruct;
    private String loanType;
    private String offerType;
    private Integer ediCount;
    private Integer processingFee;
    private Double rateOfInterest;
    private Double initialRoi;
    private Integer tenureInMonths;
    private Integer repaymentAmount;
    private Double version;
    private Boolean discarded;
    private Double clubV2Amount;
    private Double processingFeeRate;
    private Double apr;
    private Double irr;
    private String lender;
    private List<String> eligibleLenders;
}