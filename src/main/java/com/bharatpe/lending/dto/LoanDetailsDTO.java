package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("externalLoanId")
    private String externalLoanId;
    @JsonProperty("loanAmount")
    private Double loanAmount;
    @JsonProperty("tenure")
    private String tenure;
    @JsonProperty("disbursalDate")
    private Date disbursalDate;
    @JsonProperty("interestRate")
    private Double interestRate;
    @JsonProperty("ediAmount")
    private Double ediAmount;
    @JsonProperty("remainingEdiCount")
    private Integer remainingEdiCount;
    @JsonProperty("nextEdiDate")
    private Date nextEdiDate;
    @JsonProperty("paidAmount")
    private Double paidAmount;
    @JsonProperty("tentativeClosingDate")
    private Date tentativeClosingDate;
    @JsonProperty("closingDate")
    private Date closingDate;
    @JsonProperty("repayment")
    private Double repayment;
    @JsonProperty("status")
    private String status;
    @JsonProperty("loanArrangerFee")
    private SupportLoanResponseDTO.LoanArrangerFee loanArrangerFee;
    @JsonProperty("processingFee")
    private Double processingFee;
    @JsonProperty("lendingLedger")
    private List<Map<String, Object>> lendingLedger;
    @JsonProperty("arrangerFeeStatus")
    private String arrangerFeeStatus;
    @JsonProperty("nocUrl")
    private String nocUrl;
    @JsonProperty("forClosureAmount")
    private Double forClosureAmount;
    @JsonProperty("lender")
    private String lender;
    @JsonProperty("disbursalUtr")
    private  String disbursalUtr;

}
