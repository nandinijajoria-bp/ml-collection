package com.bharatpe.lending.loanV3.dto;

import com.bharatpe.lending.common.enums.LendingEnum;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.Date;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class ModifyAppRequest {
    Double loanAmount;
    Long applicationId;
    String externalLoanId;
    Long lenderDetailsId;
    Long lendingAppDetailsId;
    String lender;
    String stage;
    String breStatus;
    String appStatus;
    String kycStatus;
    String sancStatus;
    String drawdownStatus;
    String lenderDetailStatus;
    String ediModel;
    Boolean ediModelModified;
    String lmsStage;
    String nbfcId;
    String sendToNbfc;
    Boolean lenderAssc;
    Double repaymentAmount;
    Integer payableDays;
    Double edi;
    String lan;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    Date nbfcSendDate;
    String disbursalPartner;
    String loanDisbursalStatus;
    Long lpsId;
    String lpsStatus;
    Integer tenure;
    Boolean updateApr;
    Double processingFee;
    Double disbursalAmt;
    String utr;

    String applicationList;

    String docs;

    String version;
    String leadId;

    String failedUpload;

    String docUploadStatus;

    Boolean docStatusUpdate = Boolean.FALSE;

    String laldLender;

    Date lpsStartDate;

    Date disbursalDate;
}
