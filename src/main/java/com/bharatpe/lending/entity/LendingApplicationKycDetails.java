package com.bharatpe.lending.entity;

import com.bharatpe.lending.common.entity.BaseEntity;
import lombok.Data;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Date;

@Data
@Entity
@Table(name = "lending_application_kyc_details")
public class LendingApplicationKycDetails extends BaseEntity {

    @Column(name = "application_id")
    private long applicationId;

    @Column(name = "merchant_id")
    private long merchantId;

    @Column(name = "lender")
    private String lender;

    @Column(name = "pan")
    private String pan;

    @Column(name = "aadhar_identifier")
    private String aadharIdentifier;

    @Column(name = "aadhar_address")
    private String aadharAddress;

    @Column(name = "consent_date")
    private Date consentDate;

    @Column(name = "pan_url")
    private String panUrl;

    @Column(name = "aadhar_xml")
    private String aadharXml;

    @Column(name = "selfie_url")
    private String selfieUrl;

    @Column(name = "pan_approved_at")
    private Date panApprovedAt;

    @Column(name = "aadhar_approved_at")
    private Date aadharApprovedAt;

    @Column(name = "selfie_approved_at")
    private Date selfieApprovedAt;
}