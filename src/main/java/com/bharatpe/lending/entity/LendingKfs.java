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

    @Column(name = "sanction_loan_agreement_doc_url")
    private String sanctionLoanAgreementDocUrl;

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

    @Column(name = "nbfc_signed_at")
    private Date nbfcSignedAt;
}
