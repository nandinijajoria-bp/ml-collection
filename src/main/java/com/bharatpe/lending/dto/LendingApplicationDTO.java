package com.bharatpe.lending.dto;


import com.bharatpe.common.entities.LendingApplication;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import org.springframework.util.ObjectUtils;

import java.util.Date;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class LendingApplicationDTO {
    private Long id;
    private Date createdAt;
    private Date updatedAt;
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


    public static LendingApplicationDTO from(LendingApplication lendingApplication) {
        if (ObjectUtils.isEmpty(lendingApplication)) {
            return null;
        }

        LendingApplicationDTO lendingApplicationDTO = LendingApplicationDTO.builder()
          .id(lendingApplication.getId())
          .createdAt(lendingApplication.getCreatedAt())
          .updatedAt(lendingApplication.getUpdatedAt())
          .merchantId(lendingApplication.getMerchantId())
          .email(lendingApplication.getEmail())
          .shopNumber(lendingApplication.getShopNumber())
          .streetAddress(lendingApplication.getStreetAddress())
          .area(lendingApplication.getArea())
          .landmark(lendingApplication.getLandmark())
          .pincode(lendingApplication.getPincode())
          .city(lendingApplication.getCity())
          .state(lendingApplication.getState())
          .businessName(lendingApplication.getBusinessName())
          .loanAmount(lendingApplication.getLoanAmount())
          .edi(lendingApplication.getEdi())
          .ioEdi(lendingApplication.getIoEdi())
          .category(lendingApplication.getCategory())
          .processingFee(lendingApplication.getProcessingFee())
          .repayment(lendingApplication.getRepayment())
          .tenure(lendingApplication.getTenure())
          .tenureInMonths(lendingApplication.getTenureInMonths())
          .payableDays(lendingApplication.getPayableDays())
          .ioPayableDays(lendingApplication.getIoPayableDays())
          .ediFreeDays(lendingApplication.getEdiFreeDays())
          .interestRate(lendingApplication.getInterestRate())
          .status(lendingApplication.getStatus())
          .latitude(lendingApplication.getLatitude())
          .longitude(lendingApplication.getLongitude())
          .ip(lendingApplication.getIp())
          .externalLoanId(lendingApplication.getExternalLoanId())
          .nbfcId(lendingApplication.getNbfcId())
          .manualKyc(lendingApplication.getManualKyc())
          .manualKycNotes(lendingApplication.getManualKycNotes())
          .manualKycAdditionalInfo(lendingApplication.getManualKycAdditionalInfo())
          .manualCibil(lendingApplication.getManualCibil())
          .manualCibilNotes(lendingApplication.getManualCibilNotes())
          .manualCibilAdditionalInfo(lendingApplication.getManualCibilAdditionalInfo())
          .manualCibilDeviation(lendingApplication.getManualCibilDeviation())
          .manualCibilDeviationOtherReason(lendingApplication.getManualCibilDeviationOtherReason())
          .physicalVerificationStatus(lendingApplication.getPhysicalVerificationStatus())
          .physicalVerificationAdditionalInfo(lendingApplication.getPhysicalVerificationAdditionalInfo())
          .physicalVerificationNotes(lendingApplication.getPhysicalVerificationNotes())
          .sendToNbfc(lendingApplication.getSendToNbfc())
          .loanDisbursalStatus(lendingApplication.getLoanDisbursalStatus())
          .approvedBy(lendingApplication.getApprovedBy())
          .lender(lendingApplication.getLender())
          .agreement(lendingApplication.getAgreement())
          .agreementAt(lendingApplication.getAgreementAt())
          .addressProofCity(lendingApplication.getAddressProofCity())
          .addressProofState(lendingApplication.getAddressProofState())
          .addressProofZipCode(lendingApplication.getAddressProofZipCode())
          .addressProofAddress(lendingApplication.getAddressProofAddress())
          .loanConstruct(lendingApplication.getLoanConstruct())
          .mode(lendingApplication.getMode())
          .disbursalAmount(lendingApplication.getDisbursalAmount())
          .totalLoansCount(lendingApplication.getTotalLoansCount())
          .nachType(lendingApplication.getNachType())
          .nachLender(lendingApplication.getNachLender())
          .nachReferenceNumber(lendingApplication.getNachReferenceNumber())
          .nachStatus(lendingApplication.getNachStatus())
          .nachBankComment(lendingApplication.getNachBankComment())
          .loanType(lendingApplication.getLoanType())
          .alternateMobile(lendingApplication.getAlternateMobile())
          .alternateName(lendingApplication.getAlternateName())
          .verifyPan(lendingApplication.getVerifyPan())
          .verifyOcr(lendingApplication.getVerifyOcr())
          .disburseTimestamp(lendingApplication.getDisburseTimestamp())
          .disbursalPartner(lendingApplication.getDisbursalPartner())
          .kycApprovedDate(lendingApplication.getKycApprovedDate())
          .cibilApprovedDate(lendingApplication.getCibilApprovedDate())
          .physicalApprovedDate(lendingApplication.getPhysicalApprovedDate())
          .accountType(lendingApplication.getAccountType())
          .kycAssignedAt(lendingApplication.getKycAssignedAt())
          .assignedAt(lendingApplication.getAssignedAt())
          .cpvSubmitTimestamp(lendingApplication.getCpvSubmitTimestamp())
          .cpvCloseDate(lendingApplication.getCpvCloseDate())
          .nbfcSendDate(lendingApplication.getNbfcSendDate())
          .responseCode(lendingApplication.getResponseCode())
          .cpvAgentId(lendingApplication.getCpvAgentId())
          .manualKycReason(lendingApplication.getManualKycReason())
          .manualCibilReason(lendingApplication.getManualCibilReason())
          .physicalReason(lendingApplication.getPhysicalReason())
          .ckycId(lendingApplication.getCkycId())
          .ckycStatus(lendingApplication.getCkycStatus())
          .ckycRejectionReason(lendingApplication.getCkycRejectionReason())
          .ckycDate(lendingApplication.getCkycDate())
          .lmsStage(lendingApplication.getLmsStage())
          .build();

        return lendingApplicationDTO;
    }
}
