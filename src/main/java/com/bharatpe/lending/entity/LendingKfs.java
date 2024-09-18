package com.bharatpe.lending.entity;

import com.bharatpe.lending.common.entity.BaseEntity;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Date;

@Data
@Entity
@Table(name = "lending_kfs")
public class LendingKfs extends BaseEntity {

    @Column(name = "application_id")
    private long applicationId;

    @Column(name = "merchant_id")
    private long merchantId;

    @Column(name = "lender")
    private String lender;

    @Column(name = "apr")
    private Double apr;

    @Column(name = "kfs_doc_url")
    private String kfsDocUrl;

    @Column(name = "kfs_doc")
    private String kfsDocFile;

    @Column(name = "sanction_loan_agreement_doc_url")
    private String sanctionLoanAgreementDocUrl;

    @Column(name = "sanction_loan_agreement_doc")
    private String sanctionLoanAgreementDocFile;

    @Column(name = "kfs_signed_at")
    private Date kfsSignedAt;

    @Column(name = "sanction_loan_agreement_signed_at")
    private Date sanctionLoanAgreementSignedAt;

    @Column(name = "sms_send_at")
    private Date smsSendAt;

    @Column(name = "whatsapp_send_at")
    private Date whatsappSendAt;

    @Column(name = "message_delivery_confirmation")
    private Date messageDeliveryConfirmation;

    @Column(name = "message")
    private String message;

    @Column(name = "welcome_doc_url")
    private String welcomeDocUrl;

    @Column(name = "welcome_doc")
    private String welcomeDocFile;

    @Column(name = "nbfc_signed_at")
    private Date nbfcSignedAt;

    @Column(name = "signed_kfs_doc_url")
    private String signedKfsDocUrl;

    @Column(name = "signed_sanction_doc_url")
    private String signedSanctionDocUrl;

    @Column(name = "signed_kfs_doc")
    private String signedKfsDocFile;

    @Column(name = "signed_sanction_doc")
    private String signedSanctionDocFile;

    @Column(name = "authorization_letter_doc")
    private String authorizationLetterDocFile;

    @Column(name = "authorization_letter_doc_url")
    private String authorizationLetterDocUrl;

    @Column(name = "authorization_letter_signed_at")
    private Date authorizationLetterSignedAt;

    @Column(name = "mitc_doc")
    private String mitcDocFile;

    @Column(name = "gtc_doc")
    private String gtcDocFile;

    @Column(name = "loa_doc")
    private String loaDocFile;

    @Column(name = "application_form_doc")
    private String applicationFormDocFile;

    @Column(name = "mitc_doc_url")
    private String mitcDocUrl;

    @Column(name = "gtc_doc_url")
    private String gtcDocUrl;

    @Column(name = "loa_doc_url")
    private String loaDocUrl;

    @Column(name = "application_form_doc_url")
    private String applicationFormDocUrl;

}
