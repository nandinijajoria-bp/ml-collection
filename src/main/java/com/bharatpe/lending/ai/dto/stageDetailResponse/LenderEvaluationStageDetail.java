package com.bharatpe.lending.ai.dto.stageDetailResponse;

import com.bharatpe.lending.common.query.entity.LendingApplicationLenderDetailsSlave;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LenderEvaluationStageDetail {
    private Long applicationId;
    private String lender;
    private String nbfcId;
    private String breStatus;
    private Date breCompletionTimestamp;
    private Double nbfcApprovedLoanOfferAmt;
    private Integer tenure;
    private Double roi;
    private String kycStatus;
    private Date kycCompletionTimestamp;
    private String dealId;
    private Date dealGenerationTimestamp;
    private String dealNo;
    private Date loanCreationTimestamp;
    private String drawDownStatus;
    private String stage;
    private String status;
    private String accountState;
    private String kycMode;
    private Double approvedOfferLimit;
    private Double annualRoi;
    private String sanctionStatus;
    private String docUploadStatus;
    private String failedUpload;
    private String dataUploadStatus;
    private String digitalDataUploadStatus;
    private String leadStatus;
    private Boolean eSignedKfs;
    private Boolean eSignedSanc;
    private Boolean commsSent;

    public LenderEvaluationStageDetail(LendingApplicationLenderDetailsSlave slave) {
        this.applicationId = slave.getApplicationId();
        this.lender = slave.getLender();
        this.nbfcId = slave.getNbfcId();
        this.breStatus = slave.getBreStatus();
        this.breCompletionTimestamp = slave.getBreCompletionTimestamp();
        this.nbfcApprovedLoanOfferAmt = slave.getNbfcApprovedLoanOfferAmt();
        this.tenure = slave.getTenure();
        this.roi = slave.getRoi();
        this.kycStatus = slave.getKycStatus();
        this.kycCompletionTimestamp = slave.getKycCompletionTimestamp();
        this.dealId = slave.getDealId();
        this.dealGenerationTimestamp = slave.getDealGenerationTimestamp();
        this.dealNo = slave.getDealNo();
        this.loanCreationTimestamp = slave.getLoanCreationTimestamp();
        this.drawDownStatus = slave.getDrawDownStatus();
        this.stage = slave.getStage();
        this.status = slave.getStatus();
        this.accountState = slave.getAccountState();
        this.kycMode = slave.getKycMode();
        this.approvedOfferLimit = slave.getApprovedOfferLimit();
        this.annualRoi = slave.getAnnualRoi();
        this.sanctionStatus = slave.getSanctionStatus();
        this.docUploadStatus = slave.getDocUploadStatus();
        this.failedUpload = slave.getFailedUpload();
        this.dataUploadStatus = slave.getDataUploadStatus();
        this.digitalDataUploadStatus = slave.getDigitalDataUploadStatus();
        this.leadStatus = slave.getLeadStatus();
        this.eSignedKfs = slave.getESignedKfs();
        this.eSignedSanc = slave.getESignedSanc();
        this.commsSent = slave.getCommsSent();
    }
}
