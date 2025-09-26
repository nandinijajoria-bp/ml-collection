package com.bharatpe.lending.dto;

import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.lendingplatform.lms.dto.response.LoanDetailsResponse;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

import static com.bharatpe.lending.util.CommonUtil.convertBigDecimalToDouble;

@Data
@NoArgsConstructor
@Slf4j
public class LendingPaymentScheduleDTO {

    private Long id; // Primary key from lending_payment_schedule
    private Long merchantId;
    private Long merchantStoreId;
    private String loanType;
    private Long applicationId;
    private Double loanAmount;
    private Double ediAmount; // maps to ediAmount from DB or API
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
    private Double paidAmount; // maps to paidAmount from DB or API
    private Double totalCashbackAmount;
    private Double totalPenaltyAmount;
    private Date nextEdiDate;
    private String status;
    private String offDay;
    private Double totalPayableAmount;
    private String mobile;
    private String nbfc;
    private Date closingDate;
    private Date tentativeClosingDate;
    private String loanConstruct;
    private Double interest;
    private Double otherCharges;
    private Double duePrinciple; // maps to duePrinciple from DB or API
    private Double dueInterest;
    private Double dueOtherCharges;
    private Double duePenalty;
    private Double paidPrinciple; // maps to paidPrinciple from DB or API
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
    private String lmsSource;

    // Additional fields for DPD calculation
    @JsonIgnore
    private Integer maxDPD;
    @JsonIgnore
    private String dpdSummary;
    private Date createdAt;
    private Date updatedAt;

    // LendingApplication details
    private LendingApplicationDTO loanApplication;

    /**
     * Creates DTO from LendingPaymentSchedule entity (for non-1LMS loans)
     */
    public static LendingPaymentScheduleDTO fromEntity(LendingPaymentScheduleSlave entity) {
        if (entity == null) {
            return null;
        }

        LendingPaymentScheduleDTO dto = new LendingPaymentScheduleDTO();
        dto.setId(entity.getId());
        dto.setMerchantId(entity.getMerchantId());
        dto.setMerchantStoreId(entity.getMerchantStoreId());
        dto.setLoanType(entity.getLoanType());
        dto.setApplicationId(entity.getApplicationId());
        dto.setLoanAmount(entity.getLoanAmount());
        dto.setEdiAmount(entity.getEdiAmount());
        dto.setStartDate(entity.getStartDate());
        dto.setEdiCount(entity.getEdiCount());
        dto.setInterestOnlyEdiAmount(entity.getInterestOnlyEdiAmount());
        dto.setInterestOnlyStartDate(entity.getInterestOnlyStartDate());
        dto.setInterestOnlyEdiCount(entity.getInterestOnlyEdiCount());
        dto.setRemainingInterestOnlyEdiCount(entity.getRemainingInterestOnlyEdiCount());
        dto.setOverdueIntrestRate(entity.getOverdueIntrestRate());
        dto.setOverdueEdiCount(entity.getOverdueEdiCount());
        dto.setOverdueAmount(entity.getOverdueAmount());
        dto.setIncentiveAmount(entity.getIncentiveAmount());
        dto.setEdiRemainingCount(entity.getEdiRemainingCount());
        dto.setDueAmount(entity.getDueAmount());
        dto.setPaidAmount(entity.getPaidAmount());
        dto.setTotalCashbackAmount(entity.getTotalCashbackAmount());
        dto.setTotalPenaltyAmount(entity.getTotalPenaltyAmount());
        dto.setNextEdiDate(entity.getNextEdiDate());
        dto.setStatus(entity.getStatus());
        dto.setOffDay(entity.getOffDay());
        dto.setTotalPayableAmount(entity.getTotalPayableAmount());
        dto.setMobile(entity.getMobile());
        dto.setNbfc(entity.getNbfc());
        dto.setClosingDate(entity.getClosingDate());
        dto.setTentativeClosingDate(entity.getTentativeClosingDate());
        dto.setLoanConstruct(entity.getLoanConstruct());
        dto.setInterest(entity.getInterest());
        dto.setOtherCharges(entity.getOtherCharges());
        dto.setDuePrinciple(entity.getDuePrinciple());
        dto.setDueInterest(entity.getDueInterest());
        dto.setDueOtherCharges(entity.getDueOtherCharges());
        dto.setDuePenalty(entity.getDuePenalty());
        dto.setPaidPrinciple(entity.getPaidPrinciple());
        dto.setPaidInterest(entity.getPaidInterest());
        dto.setPaidOtherCharges(entity.getPaidOtherCharges());
        dto.setPaidPenalty(entity.getPaidPenalty());
        dto.setDisbursalSettlementId(entity.getDisbursalSettlementId());
        dto.setCreditLoan(entity.getCreditLoan());
        dto.setTlDetailsId(entity.getTlDetailsId());
        dto.setLenderDisbursalNotify(entity.getLenderDisbursalNotify());
        dto.setAdjustedDueAmount(entity.getAdjustedDueAmount());
        dto.setAdjustedPaidAmount(entity.getAdjustedPaidAmount());
        dto.setSettlementStatus(entity.getSettlementStatus());
        dto.setLmsSource(entity.getLmsSource());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    /**
     * Creates DTO from LendingPaymentSchedule entity enriched with Lentra API data (for 1LMS loans)
     */
    public static LendingPaymentScheduleDTO fromEntityWithApiData(LendingPaymentScheduleSlave entity,
                                                                  LoanDetailsResponse.LoanSummary apiData) {
        if (entity == null) {
            return null;
        }

        LendingPaymentScheduleDTO dto = fromEntity(entity);

        log.info("Populating LendingPaymentScheduleDTO from entity and API data. Entity ID: {}, API Data: {}",
                entity.getId(), apiData);
        // Override with API data for 1LMS loans
        log.info("API Data received: {}", apiData);
        if (apiData != null) {
            dto.setPaidAmount(convertBigDecimalToDouble(apiData.getTotalPaidAmount()));
            dto.setEdiAmount(convertBigDecimalToDouble(apiData.getInstalmentAmount()));
            dto.setPaidPrinciple(convertBigDecimalToDouble(apiData.getPaidPrincipalAmount()));
            dto.setPaidInterest(convertBigDecimalToDouble(apiData.getPaidInterestAmount()));
            dto.setDuePrinciple(convertBigDecimalToDouble(apiData.getOverduePrincipal()));
            dto.setDueInterest(convertBigDecimalToDouble(apiData.getOverdueInterest()));
            dto.setDpdSummary(apiData.getDpdSummary());
            dto.setOverdueAmount(convertBigDecimalToDouble(apiData.getOverdueInstalmentAmount()));
            // Calculate maxDPD from dpdSummary if available
            if (apiData.getDpdSummary() != null) {
                dto.setMaxDPD(calculateMaxDpdFromSummary(apiData.getDpdSummary()));
            }
        }
        return dto;
    }

    /**
     * Extracts the maximum DPD from dpdSummary string
     * Example: "0|0|0|...|13|" -> returns 13
     */
    private static Integer calculateMaxDpdFromSummary(String dpdSummary) {
        if (dpdSummary == null || dpdSummary.isEmpty()) {
            return 0;
        }

        try {
            String[] dpdValues = dpdSummary.split("\\|");
            int maxDpd = 0;

            for (String dpdValue : dpdValues) {
                if (!dpdValue.trim().isEmpty()) {
                    try {
                        int dpd = Integer.parseInt(dpdValue.trim());
                        maxDpd = Math.max(maxDpd, dpd);
                    } catch (NumberFormatException e) {
                        // Skip invalid values
                    }
                }
            }

            return maxDpd;
        } catch (Exception e) {
            return 0;
        }
    }
}
