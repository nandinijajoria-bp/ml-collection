package com.bharatpe.lending.ai.dto;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiLendingPaymentSchedule extends AiBaseEntity{
    private Long merchantId;
    private Long merchantStoreId;
    private String loanType;
    private AiLendingApplication loanApplication;
    private Double loanAmount;
    private Double ediAmount;
    private Date startDate;
    private Integer ediCount;
    private Double interestOnlyEdiAmount;
    private Date interestOnlyStartDate;
    private Integer interestOnlyEdiCount;
    private Integer remainingInterestOnlyEdiCount;
    private Double overdueIntrestRate;
    private Integer overdueEdiCount;
    private Double overdueAmount;
    private Double incentiveAmount;
    private Integer ediRemainingCount;
    private Double dueAmount;
    private Double paidAmount;
    private Double totalCashbackAmount;
    private Double totalPenaltyAmount;
    private Date nextEdiDate;
    private String status;
    private String offDay;
    private Long applicationId;
    private Double totalPayableAmount;
    private String nbfc;
    private Date closingDate;
    private Date tentativeClosingDate;
    private String loanConstruct;
    private Double interest;
    private Double otherCharges;
    private Double duePrinciple;
    private Double dueInterest;
    private Double dueOtherCharges;
    private Double duePenalty;
    private Double paidPrinciple;
    private Double paidInterest;
    private Double paidOtherCharges;
    private Double paidPenalty;
    private Integer disbursalSettlementId;
    private Boolean creditLoan;
    private Long tlDetailsId;
    private String lenderDisbursalNotify;
    private Double adjustedDueAmount;
    private Double adjustedPaidAmount;
    private String settlementStatus;
    private String settlementMechanism;
    private Boolean settleAllPrinciple;
    private String writeoffFor;
    private Double lastOverDueAmount;
    private Boolean isSettlementInitiated;
    private Date settlementDate;
    private Boolean isNpa;
    private String lmsSource;

    public AiLendingPaymentSchedule(LendingPaymentSchedule src) {
        super(src);
        if (src != null) {
            this.merchantId = src.getMerchantId();
            this.merchantStoreId = src.getMerchantStoreId();
            this.loanType = src.getLoanType();
            this.loanApplication = new AiLendingApplication(src.getLoanApplication());
            this.loanAmount = src.getLoanAmount();
            this.ediAmount = src.getEdiAmount();
            this.startDate = src.getStartDate();
            this.ediCount = src.getEdiCount();
            this.interestOnlyEdiAmount = src.getInterestOnlyEdiAmount();
            this.interestOnlyStartDate = src.getInterestOnlyStartDate();
            this.interestOnlyEdiCount = src.getInterestOnlyEdiCount();
            this.remainingInterestOnlyEdiCount = src.getRemainingInterestOnlyEdiCount();
            this.overdueIntrestRate = src.getOverdueIntrestRate();
            this.overdueEdiCount = src.getOverdueEdiCount();
            this.overdueAmount = src.getOverdueAmount();
            this.incentiveAmount = src.getIncentiveAmount();
            this.ediRemainingCount = src.getEdiRemainingCount();
            this.dueAmount = src.getDueAmount();
            this.paidAmount = src.getPaidAmount();
            this.totalCashbackAmount = src.getTotalCashbackAmount();
            this.totalPenaltyAmount = src.getTotalPenaltyAmount();
            this.nextEdiDate = src.getNextEdiDate();
            this.status = src.getStatus();
            this.offDay = src.getOffDay();
            this.applicationId = src.getApplicationId();
            this.totalPayableAmount = src.getTotalPayableAmount();
            this.nbfc = src.getNbfc();
            this.closingDate = src.getClosingDate();
            this.tentativeClosingDate = src.getTentativeClosingDate();
            this.loanConstruct = src.getLoanConstruct();
            this.interest = src.getInterest();
            this.otherCharges = src.getOtherCharges();
            this.duePrinciple = src.getDuePrinciple();
            this.dueInterest = src.getDueInterest();
            this.dueOtherCharges = src.getDueOtherCharges();
            this.duePenalty = src.getDuePenalty();
            this.paidPrinciple = src.getPaidPrinciple();
            this.paidInterest = src.getPaidInterest();
            this.paidOtherCharges = src.getPaidOtherCharges();
            this.paidPenalty = src.getPaidPenalty();
            this.disbursalSettlementId = src.getDisbursalSettlementId();
            this.creditLoan = src.getCreditLoan();
            this.tlDetailsId = src.getTlDetailsId();
            this.lenderDisbursalNotify = src.getLenderDisbursalNotify();
            this.adjustedDueAmount = src.getAdjustedDueAmount();
            this.adjustedPaidAmount = src.getAdjustedPaidAmount();
            this.settlementStatus = src.getSettlementStatus();
            this.settlementMechanism = src.getSettlementMechanism();
            this.settleAllPrinciple = src.getSettleAllPrinciple();
            this.writeoffFor = src.getWriteoffFor();
            this.lastOverDueAmount = src.getLastOverDueAmount();
            this.isSettlementInitiated = src.getSettlementInitiated();
            this.settlementDate = src.getSettlementDate();
            this.isNpa = src.getNpa();
            this.lmsSource = src.getLmsSource();
        }
    }
}
