package com.bharatpe.lending.loanV3.dto.response.ugro;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UgroGetLeadResponse {
    private String leadId;
    private Boolean isLeadExpired;
    private Integer userId;
    private String product;
    private Long createdAt;
    private Long latestUpdatedAt;
    private String status;
    private ApprovedParameters approvedParameters;
    private String kycValidation;
    private String bankAccountVerification;
    private String businessProofVerification;
    private KYBRemarks kybRemarks;
    private String unsignedAgreementGeneration;
    private String esignedAgreementGeneration;
    private String repayInstrumentRegistration;
    private List<String> remarks;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApprovedParameters {
        private Integer processingFee;
        private Integer interestPercent;
        private Integer emiAmount;
        @JsonProperty("opportunity_id")
        private String opportunityId;
        @JsonProperty("application_id")
        private String applicationId;
        private Integer amount;
        private Integer tenureMonths;
        private String productCode;
        @JsonProperty("loan_id")
        private String loanId;
        private Integer dueDayOfMonth;
        private Double convenienceFeePct;
        private Double installmentAmount;
        private Long validUntil;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class KYBRemarks {
        private String udyamFetchStatus;
        private String udyamFormFilled;
    }
}

