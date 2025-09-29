package com.bharatpe.lending.ai.dto.stageDetailResponse;

import com.bharatpe.lending.entity.LendingKfs;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import java.util.Date;

@Data
@NoArgsConstructor
public class KfsStageDetail {
    private long applicationId;
    private long merchantId;
    private String lender;
    private Double apr;
    private String kfsDocUrl;
    private String kfsDocFile;
    private String sanctionLoanAgreementDocUrl;
    private String sanctionLoanAgreementDocFile;
    private Date kfsSignedAt;
    private Date sanctionLoanAgreementSignedAt;
    private Date smsSendAt;
    private Date whatsappSendAt;
    private Date messageDeliveryConfirmation;
    private String message;
    private String welcomeDocUrl;
    private String welcomeDocFile;
    private Date nbfcSignedAt;
    private String signedKfsDocUrl;
    private String signedSanctionDocUrl;
    private String signedKfsDocFile;
    private String signedSanctionDocFile;
    private String authorizationLetterDocFile;
    private String authorizationLetterDocUrl;
    private Date authorizationLetterSignedAt;
    private String mitcDocFile;
    private String gtcDocFile;
    private String loaDocFile;
    private String applicationFormDocFile;
    private String mitcDocUrl;
    private String gtcDocUrl;
    private String loaDocUrl;
    private String applicationFormDocUrl;
    private String docLanguage;

    public KfsStageDetail(LendingKfs lendingKfs){
        this.lender = lendingKfs.getLender();
        this.apr = lendingKfs.getApr();
        this.kfsDocFile = lendingKfs.getKfsDocFile();
        this.kfsSignedAt = lendingKfs.getKfsSignedAt();
        this.sanctionLoanAgreementSignedAt = lendingKfs.getSanctionLoanAgreementSignedAt();
        this.nbfcSignedAt = lendingKfs.getNbfcSignedAt();
        this.authorizationLetterSignedAt = lendingKfs.getAuthorizationLetterSignedAt();
        this.docLanguage = lendingKfs.getDocLanguage();
    }
}
