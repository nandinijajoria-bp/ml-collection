package com.bharatpe.lending.ai.dto;

import com.bharatpe.common.entities.LendingLedger;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LedgerApiResponse {
    private boolean success;
    private String message;
    private List<List<LedgerData>> data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LedgerData {
        private Long id;
        private Date createdAt;
        private Date updatedAt;
        private Long merchantId;
        private LendingPaymentSchedule lendingPaymentSchedule;
        private String txnType;
        private Date date;
        private Double amount;
        private Double principle;
        private Double interest;
        private Double otherCharges;
        private Double penalty;
        private String description; // sometimes present
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LendingPaymentSchedule {
        private Long id;
        private Date createdAt;
        private Date updatedAt;
        private Long merchantId;
        private String loanType;
        private LoanApplication loanApplication;
        private Double loanAmount;
        private Double ediAmount;
        private Date startDate;
        private Integer ediCount;
        private Integer overdueEdiCount;
        private Double overdueAmount;
        private Double paidAmount;
        private Double dueAmount;
        private Double totalPenaltyAmount;
        private String status;
        private Long applicationId;
        private Double totalPayableAmount;
        private String mobile;
        private String nbfc;
        private Date tentativeClosingDate;
        private Double interest;
        private Double duePrinciple;
        private Double dueInterest;
        private Double duePenalty;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LoanApplication {
        private Long id;
        private Date createdAt;
        private Date updatedAt;
        private Long merchantId;
        private String shopNumber;
        private String streetAddress;
        private String area;
        private Integer pincode;
        private String city;
        private String state;
        private String businessName;
        private Double loanAmount;
        private Double edi;
        private Double processingFee;
        private Double repayment;
        private String tenure;
        private Integer tenureInMonths;
        private Double interestRate;
        private String status;
        private String externalLoanId;
        private String lender;
        private String loanType;
        private Date disburseTimestamp;
        private String accountType;
        private String ckycStatus;
        private String lmsStage;
        private String merchantName;
    }
}
