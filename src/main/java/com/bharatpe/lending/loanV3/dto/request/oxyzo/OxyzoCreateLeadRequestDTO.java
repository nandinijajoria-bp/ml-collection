package com.bharatpe.lending.loanV3.dto.request.oxyzo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OxyzoCreateLeadRequestDTO {

    private String mobileNum;
    private String customerId;
    private String fullName;
    private String email;
    private String gender;
    private Long dob;
    private String pan;
    private String aadhaarNo;
    private String residentBifurcation;
    private String currentAddress;
    private String currentPincode;
    private boolean permanentAddressSameAsCurrentAddress;
    private String permanentAddress;
    private String permanentPincode;
    private String shopName;
    private String shopAddress;
    private String shopPincode;
    private String riskGroup;
    private String pincodeCategory;
    private String merchantCategory;
    private Integer vintageOnPlatform;
    private BigDecimal tpv;
    private BigDecimal tpvMultiplier;
    private BigDecimal dailyTpv;
    private BigDecimal ediDailyTpvRatio;
    private String customerType;
    private String paymentFrequency;
    private String loanType;
    private OxyzoKycDetailsRequestDto kycDetails;
    private String loanSegment;
    private BigDecimal interestRate;
    private BigDecimal loanAmount;
    private Integer tenure;
    private String shopGeoTagLatitude;
    private String shopGeoTagLongitude;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Builder
    public static class OxyzoKycDetailsRequestDto {
        private String type;
        private boolean isVerified;
    }

}
