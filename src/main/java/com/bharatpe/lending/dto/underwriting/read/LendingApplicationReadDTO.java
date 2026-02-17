package com.bharatpe.lending.dto.underwriting.read;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class LendingApplicationReadDTO {

    private Long merchantId;
    private String email;
    private String shopNumber;
    private String streetAddress;
    private String area;
    private String landmark;
    private Long pincode;
    private String city;
    private String state;
    private String businessName;
    private Double loanAmount;
    private Double edi;
    private Double ioEdi;
    private String category;
    private Double processingFee;
    private Double repayment;
    private String tenure;
    private Integer tenureInMonths;
    private Long payableDays;
    private Integer ioPayableDays;
    private Integer ediFreeDays;
    private Double interestRate;
    private String status;
    private String latitude;
    private String longitude;
    private String ip;
    private String externalLoanId;
    private String nbfcId;
    private String manualKyc;
    private String manualKycNotes;
    private String manualKycAdditionalInfo;
    private String manualCibil;
    private String manualCibilNotes;
    private String manualCibilAdditionalInfo;
    private String manualCibilDeviation;
    private String manualCibilDeviationOtherReason;
    private String physicalVerificationStatus;
    private String physicalVerificationAdditionalInfo;
    private String physicalVerificationNotes;
    private String sendToNbfc;
    private String loanDisbursalStatus;
    private Long approvedBy;
    private String lender;
    private int agreement;
    private Date agreementAt;
    private String addressProofCity;
    private String addressProofState;
    private String addressProofZipCode;
    private String addressProofAddress;
    private String loanConstruct;
    private String mode;
    private Double disbursalAmount;
    private Integer totalLoansCount;
    private String nachType;
    private String nachLender;
    private String nachReferenceNumber;
    private String nachStatus;
    private String nachBankComment;
    private String loanType;
    private String alternateMobile;
    private String alternateName;
    private String verifyPan;
    private String verifyOcr;
    private Date disburseTimestamp;
    private String disbursalPartner;
    private Date kycApprovedDate;
    private Date cibilApprovedDate;
    private Date physicalApprovedDate;
    private String accountType;
    private Date kycAssignedAt;
    private Date assignedAt;
    private Date cpvSubmitTimestamp;
    private Date cpvCloseDate;
    private Date nbfcSendDate;
    private String responseCode;
    private Long cpvAgentId;
    private String manualKycReason;
    private String manualCibilReason;
    private String physicalReason;
    private String ckycId;
    private String ckycStatus;
    private String ckycRejectionReason;
    private Date ckycDate;
    private String lmsStage;
    private String rejectionReason;
    private Long id;
    private Date createdAt;
    private Date updatedAt;
}
