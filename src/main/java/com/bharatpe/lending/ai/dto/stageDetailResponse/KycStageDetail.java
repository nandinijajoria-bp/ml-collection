package com.bharatpe.lending.ai.dto.stageDetailResponse;

import com.bharatpe.lending.common.entity.LendingApplicationKycDetails;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
public class KycStageDetail {
    private String lender;
    private Date consentDate;
    private Date panApprovedAt;
    private Date aadharApprovedAt;
    private Date selfieApprovedAt;
    private Date kycInitiatedAt;
    private String gender;

    public KycStageDetail(LendingApplicationKycDetails kycDetails) {
        this.lender = kycDetails.getLender();
        this.consentDate = kycDetails.getConsentDate();
        this.panApprovedAt = kycDetails.getPanApprovedAt();
        this.aadharApprovedAt = kycDetails.getAadharApprovedAt();
        this.selfieApprovedAt = kycDetails.getSelfieApprovedAt();
        this.kycInitiatedAt = kycDetails.getKycInitiatedAt();
        this.gender = kycDetails.getGender();
    }
}
