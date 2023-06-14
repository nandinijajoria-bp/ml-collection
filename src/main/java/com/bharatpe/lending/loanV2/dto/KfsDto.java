package com.bharatpe.lending.loanV2.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KfsDto {
    private long merchantId;
    private long applicationId;
    private String externalLoanId;
    private String lender;
    private String lenderCorporateName;
    private String lenderBusinessAddress;
    private String colenderCorporateName;
    private String colenderBusinessAddress;
    private Double loanAmount;
    private Double processingFee;
    private Double processingFeePercentage;
    private Double processingFeePercentageWithoutGst;
    private Integer tenureInMonths;
    private Double disbursalAmount;
    private Double repaymentAmount;
    private Double interestRate;
    private Double apr;
    private long ediCount;
    private Double ediAmount;
    private Integer coolingOffDays;
    private String lenderContactName;
    private String lenderContactEmail;
    private String lenderContactNumber;
    private String lspContactName;
    private String lspContactEmail;
    private String lspContactNumber;
    private String ediOffData;
    private String nbfcId;
    private String locationLatLong;
    private boolean isTopUpLoan;

}
