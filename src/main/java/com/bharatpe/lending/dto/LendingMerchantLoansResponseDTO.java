package com.bharatpe.lending.dto;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.query.entity.LendingApplicationSlave;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.lendingplatform.lms.dto.response.LoanDetailsResponse;
import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import com.bharatpe.lending.loanV3.dto.TopupEligibilityResponseData;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;

import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.bharatpe.lending.lendingplatform.lms.util.ConversionUtil.safeBigDecimalToDouble;
import static com.bharatpe.lending.lendingplatform.lms.util.ConversionUtil.safeBigDecimalToInt;

@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@Data
@ToString(callSuper = true)
public class LendingMerchantLoansResponseDTO extends TopupEligibilityResponseData {

    private List<Loan> loans;
    private Boolean isPanNsdlVerified;
    private Double totalPaidAmount = 0D;
    private Double totalAmount = 0D;
    private Double totalDueAmount = 0D;
    private HalfLoan halfLoan;
    private IOLoan ioLoan;
    private Boolean ediStarted;
    private Boolean perpetualDpdRestrictPgPayment;
    private List<RepaymentDetails> repaymentDetails;
    private BankAccountDetails accountDetails;
    private Boolean showChangeBankAccountBanner;
    private Boolean showRenachBanner;

    private Boolean contactSync = false;
    private List<PenaltyConfig> penaltyConfig;
    private Boolean topupRejected;
    private Boolean timeBasedTopupDisabled;
    private double configNachBounceAmount;


    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    @Data
    @Builder
    public static class HalfLoan {
        private Double oldEdiAmount;
        private Double newEdiAmount;
        private Integer oldEdiRemaining;
        private Integer newEdiRemaining;
        private Double oldRepaymentAmount;
        private Double newRepaymentAmount;
        private String category;
        private Double interestRate;
        private Integer totalRepayment;
        private Integer principalRepayment;
        private Integer interestRepayment;
        private String lender;
        private Double prevLoanUnpaidAmount;
        private Integer newEdiMonth;
        private Double prepaymentAmount;
        private Integer arrangerFee;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    @Data
    @Builder
    public static class IOLoan {
        private Double oldEdiAmount;
        private Double newEdiAmount;
        private Double newIoEdiAmount;
        private Integer oldEdiRemaining;
        private Integer newEdiRemaining;
        private Integer newIoEdiRemaining;
        private Double oldRepaymentAmount;
        private Double newRepaymentAmount;
        private String category;
        private Double interestRate;
        private Integer totalRepayment;
        private Integer principalRepayment;
        private Integer interestRepayment;
        private Integer newEdiMonth;
        private Integer newIoEdiMonth;
        private String lender;
        private Double prevLoanUnpaidAmount;
        private Double prepaymentAmount;
        private Integer arrangerFee;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RepaymentDetails {
        private String status;
        private String mode;
        private Double amount;
        private Date date;
        private String orderId;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)

    @Data
    public static class Loan {

        private String presentmentStatus;
        private Double presentmentAmount;
        private Long applicationId;
        private Long loanId;
        private Double loanAmount;
        private Double ediAmount;
        private Double dueAmount;
        private Double todayEdi;
        private Double pendingEdi;
        private Double interestRate;
        private Double processingFee;
        private Double disbursedAmount;
        private Double pendingAmount;
        private Double paidPrinciple;
        private String tenure;
        private String startDate;
        private String endDate;
        private String loanType;
        private String status;
        private Boolean autoPayEligibility;
        private String autoPayMandateStatus;
        private String mandateRegisterId;
        private Date presentmentDate;
        private Double paidAmount;
        private Double lastEdiPaid;
        private Double repaymentAmount;
        private Integer ediCount;
        private String lender;
        private Integer dpd;
        private Integer newDpd; // its stores value of loan_dpd for frontend to show message
        @JsonProperty(value = "showPaynow")
        private boolean showPaynow = true;
        @JsonProperty(value = "showCustomAmount")
        private boolean showCustomAmount = false;
        private String settlementStatus;
        private double duePenalty;
        private long ediDays;
        private double nachBounceAmount;
        private Double annualRoi;
        private double totalDue;
        private double totalExcessBalance;
        private double netPayable;
        private boolean settlementInitiated;
        private Double settlementAmountOffer;
        private Date settlementExpiryDate;

        public Loan() {
        }

        public Loan(Long applicationId,Long loanId, Double loanAmount, Double ediAmount, Double dueAmount, Double interestRate,
                Double processingFee, Double disbursedAmount, Double pendingAmount, Double paidPrinciple, String tenure,
                String startDate, String endDate, String loanType, String status, Double paidAmount,Double repaymentAmount,
                    Integer ediCount, String lender, String settlementStatus, double duePenalty, boolean settlementInitiated) {
            this.applicationId = applicationId;
            this.loanId = loanId;
            this.loanAmount = loanAmount;
            this.ediAmount = ediAmount;
            this.dueAmount = dueAmount;
            this.interestRate = interestRate;
            this.processingFee = processingFee;
            this.disbursedAmount = disbursedAmount;
            this.pendingAmount = pendingAmount;
            this.paidPrinciple = paidPrinciple;
            this.tenure = tenure;
            this.startDate = startDate;
            this.endDate = endDate;
            this.loanType = loanType;
            this.status = status;
            this.paidAmount = paidAmount;
            this.repaymentAmount = repaymentAmount;
            this.ediCount = ediCount;
            this.lender = lender;
            this.settlementStatus = settlementStatus;
            this.duePenalty = duePenalty;
            this.settlementInitiated = settlementInitiated;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PenaltyConfig {
        private Long minAmount;
        private Long maxAmount;
        private Double penalty;
    }
    public void setLoansFromLendingPaymentSchedule(List<LendingPaymentScheduleSlave> loansList,@NotNull Map<Long, LendingApplicationSlave> applicationMap) {
        this.loans = loansList.stream()
                .map(loan -> lendingPaymentScheduleToLoan(loan, applicationMap.get(loan.getApplicationId())))
                .collect(Collectors.toList());
        this.totalAmount = this.loans.stream().reduce(0D,
                (partialAmount, loan)->partialAmount + (ObjectUtils.isEmpty(loan.getLoanAmount()) ? 0 : loan.getLoanAmount()), Double::sum);
        this.totalDueAmount = this.loans.stream().reduce(0D,
                (partialAmount, loan)->partialAmount + (ObjectUtils.isEmpty(loan.getDueAmount()) ? 0 : loan.getDueAmount()) , Double::sum);
        this.totalPaidAmount = this.loans.stream().reduce(0D,
                (partialAmount, loan)->partialAmount + (ObjectUtils.isEmpty(loan.getPaidAmount()) ? 0 : loan.getPaidAmount()), Double::sum);
    }

    public void setHalfLoan(LendingPaymentScheduleSlave lendingPaymentSchedule, LoanCalculationUtil.LoanBreakupDetail loanBreakupDetail) {
        if (loanBreakupDetail == null || lendingPaymentSchedule == null) {
            this.halfLoan = null;
            return;
        }
        double foreclosureAmount = (int) Math.ceil(lendingPaymentSchedule.getLoanAmount() - (lendingPaymentSchedule.getPaidPrinciple() != null ? lendingPaymentSchedule.getPaidPrinciple() : 0) + (lendingPaymentSchedule.getDueInterest() != null ? lendingPaymentSchedule.getDueInterest() : 0));
        int pf = loanBreakupDetail.getProcessingFee() != null ? loanBreakupDetail.getProcessingFee() : 0;
        this.halfLoan = LendingMerchantLoansResponseDTO.HalfLoan.builder()
                .oldEdiAmount(lendingPaymentSchedule.getEdiAmount())
                .newEdiAmount(loanBreakupDetail.getEdi().doubleValue())
                .oldEdiRemaining(lendingPaymentSchedule.getEdiRemainingCount())
                .newEdiRemaining(loanBreakupDetail.getEdiDays())
                .oldRepaymentAmount(lendingPaymentSchedule.getTotalPayableAmount() - lendingPaymentSchedule.getPaidAmount())
                .newRepaymentAmount(loanBreakupDetail.getRepayment().doubleValue())
                .category(loanBreakupDetail.getCategory())
                .interestRate(loanBreakupDetail.getInterestRate())
                .totalRepayment(loanBreakupDetail.getRepayment())
                .principalRepayment(loanBreakupDetail.getLoanAmount())
                .interestRepayment(loanBreakupDetail.getInterestAmount())
                .lender(!Lender.LDC.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) ? Lender.LDC.name() : Lender.MAMTA.name())
                .prevLoanUnpaidAmount(foreclosureAmount)
                .newEdiMonth(loanBreakupDetail.getPrincipleEdiTenure())
                .arrangerFee(pf)
                .prepaymentAmount(loanBreakupDetail.getLoanAmount() - foreclosureAmount - pf)
                .build();
    }

    public void setIoLoan(LendingPaymentScheduleSlave lendingPaymentSchedule, LoanCalculationUtil.LoanBreakupDetail loanBreakupDetail) {
        if (loanBreakupDetail == null || lendingPaymentSchedule == null) {
            this.ioLoan = null;
            return;
        }
        double foreclosureAmount = (int) Math.ceil(lendingPaymentSchedule.getLoanAmount() - (lendingPaymentSchedule.getPaidPrinciple() != null ? lendingPaymentSchedule.getPaidPrinciple() : 0) + (lendingPaymentSchedule.getDueInterest() != null ? lendingPaymentSchedule.getDueInterest() : 0));
        int pf = loanBreakupDetail.getProcessingFee() != null ? loanBreakupDetail.getProcessingFee() : 0;
        int ediPaidCount = (int)Math.ceil(lendingPaymentSchedule.getPaidAmount()/lendingPaymentSchedule.getEdiAmount());
        int ediRemainingCount = lendingPaymentSchedule.getEdiCount() - ediPaidCount;
        this.ioLoan = LendingMerchantLoansResponseDTO.IOLoan.builder()
                .oldEdiAmount(lendingPaymentSchedule.getEdiAmount())
                .newEdiAmount(loanBreakupDetail.getEdi().doubleValue())
                .newIoEdiAmount(loanBreakupDetail.getIoEdi().doubleValue())
                .oldEdiRemaining(ediRemainingCount)
                .newEdiRemaining(loanBreakupDetail.getEdiDays())
                .newIoEdiRemaining(loanBreakupDetail.getIoEdiDays())
                .oldRepaymentAmount(lendingPaymentSchedule.getTotalPayableAmount() - lendingPaymentSchedule.getPaidAmount())
                .newRepaymentAmount(loanBreakupDetail.getRepayment().doubleValue())
                .category(loanBreakupDetail.getCategory())
                .interestRate(loanBreakupDetail.getInterestRate())
                .totalRepayment(loanBreakupDetail.getRepayment())
                .principalRepayment(loanBreakupDetail.getLoanAmount())
                .interestRepayment(loanBreakupDetail.getInterestAmount())
                .newEdiMonth(loanBreakupDetail.getPrincipleEdiTenure())
                .newIoEdiMonth(loanBreakupDetail.getIoOrFreeEdiTenure())
                .lender(Lender.LIQUILOANS_NBFC.name())
                .prevLoanUnpaidAmount(foreclosureAmount)
                .arrangerFee(pf)
                .prepaymentAmount(loanBreakupDetail.getLoanAmount() - foreclosureAmount - pf)
                .build();
    }

    private Loan lendingPaymentScheduleToLoan(LendingPaymentScheduleSlave lendingPaymentSchedule, LendingApplicationSlave application) {
        if(application == null){
            application = lendingPaymentSchedule.getLoanApplication();
        }
        Double loanAmount = lendingPaymentSchedule.getLoanAmount() != null ? lendingPaymentSchedule.getLoanAmount()
                : 0d;
        Double paidPrinciple = lendingPaymentSchedule.getPaidPrinciple() != null
                ? lendingPaymentSchedule.getPaidPrinciple()
                : 0d;
        Double ediAmount = lendingPaymentSchedule.getEdiAmount() != null ? lendingPaymentSchedule.getEdiAmount() : 0d;
        Double dueAmount = lendingPaymentSchedule.getDueAmount() != null ? lendingPaymentSchedule.getDueAmount() : 0d;
        Double dueInterest = lendingPaymentSchedule.getDueInterest() != null ? lendingPaymentSchedule.getDueInterest()
                : 0d;
        String startDate = lendingPaymentSchedule.getStartDate() != null
                ? lendingPaymentSchedule.getStartDate().toString()
                : "";
        String endDate = lendingPaymentSchedule.getTentativeClosingDate() != null
                ? lendingPaymentSchedule.getTentativeClosingDate().toString()
                : "";
        String loanType = application != null && application.getLoanType() != null ? application.getLoanType() : "";
        String status = lendingPaymentSchedule.getStatus() != null ? lendingPaymentSchedule.getStatus() : "ACTIVE";
        Double interestRate = application != null && application.getInterestRate() != null ? application.getInterestRate() : 0d;
        Double processingFee = application != null && application.getProcessingFee() != null ? application.getProcessingFee() : 0d;
        Double disbursedAmount = loanAmount - processingFee;
        String tenure = application != null && application.getTenure() != null ? application.getTenure() : "";
        Double pendingAmount = Math.max(0,loanAmount - paidPrinciple + dueInterest);
        String lender = lendingPaymentSchedule.getNbfc();
        String settlementStatus = lendingPaymentSchedule.getSettlementStatus();
        Long applicationId = lendingPaymentSchedule.getApplicationId();
        double penaltyFee = Objects.nonNull(lendingPaymentSchedule.getDuePenalty()) ? lendingPaymentSchedule.getDuePenalty() : 0d;
        boolean settlementInitiated = lendingPaymentSchedule.getSettlementInitiated();

        return new Loan(applicationId, lendingPaymentSchedule.getId(), loanAmount, ediAmount, dueAmount, interestRate, processingFee,
                disbursedAmount, pendingAmount, paidPrinciple, tenure, startDate, endDate, loanType, status, lendingPaymentSchedule.getPaidAmount(),
                lendingPaymentSchedule.getTotalPayableAmount(), lendingPaymentSchedule.getEdiCount(), lender, settlementStatus, penaltyFee, settlementInitiated);
    }

    private Loan loanDetailsUpdateFromLendingSchedule(LendingPaymentSchedule oneLmsLoan) {
        // Fill data from LA & LPS Table Data :

        LendingApplication application = oneLmsLoan.getLoanApplication();
        if (ObjectUtils.isEmpty(application)) {
            log.error("LendingApplicationSlave is null for LendingPaymentScheduleSlave with ID: {}", oneLmsLoan.getId());
            throw new RuntimeException("An unexpected error occurred while processing your request");
        }

        Long applicationId = application.getId();
        Double loanAmount = application.getLoanAmount() != null ? application.getLoanAmount() : 0d;
        Double interestRate = application.getInterestRate() != null ? application.getInterestRate() : 0d;
        Double processingFee = application.getProcessingFee() != null ? application.getProcessingFee() : 0d;
        Double disbursedAmount = loanAmount - processingFee;
        String tenure = application.getTenure() != null ? application.getTenure() : "";
        String startDate = oneLmsLoan.getStartDate() != null ? oneLmsLoan.getStartDate().toString() : "";
        String setEndDate = oneLmsLoan.getTentativeClosingDate() != null ? oneLmsLoan.getTentativeClosingDate().toString() : "";
        String loanType = application.getLoanType() != null ? application.getLoanType() : "";
        String status = oneLmsLoan.getStatus() != null ? oneLmsLoan.getStatus() : "ACTIVE";
        String lender = oneLmsLoan.getNbfc();
        String settlementStatus = oneLmsLoan.getSettlementStatus();
        boolean settlementInitiated = oneLmsLoan.getSettlementInitiated();

        // Mark for description  :
        //application.getIoPayableDays()  is EdiCount

        return new LendingMerchantLoansResponseDTO.Loan(applicationId, oneLmsLoan.getId(), loanAmount, null, null, interestRate, processingFee,
                disbursedAmount, null, null, tenure, startDate, setEndDate, loanType, status, null,
                oneLmsLoan.getTotalPayableAmount(), oneLmsLoan.getEdiCount(), lender, settlementStatus, 0.d, settlementInitiated);
    }

    public void updateFromLoanSummaryOneLms(LendingMerchantLoansResponseDTO.Loan loan, LoanDetailsResponse lmsLoanDetails, LendingPaymentSchedule lpsTable) {
        LoanDetailsResponse.LoanSummary loanSummary = getLoanSummary(lmsLoanDetails);
        if (!ObjectUtils.isEmpty(loanSummary)) {
            loan.setLoanId(lpsTable.getId());
            updateFromLoanSummary(loan, loanSummary, lpsTable);
        }
    }

    public void updateTotalAmounts(List<LendingPaymentSchedule> loansList) {
        log.info("Updating total amounts for {} loans", loansList.size());
        this.loans = loansList.stream().map(this::loanDetailsUpdateFromLendingSchedule).collect(Collectors.toList());
        this.totalAmount = this.loans.stream().reduce(0D,
                (partialAmount, loan)->partialAmount + (ObjectUtils.isEmpty(loan.getLoanAmount()) ? 0 : loan.getLoanAmount()), Double::sum);
        this.totalDueAmount = this.loans.stream().reduce(0D,
                (partialAmount, loan)->partialAmount + (ObjectUtils.isEmpty(loan.getDueAmount()) ? 0 : loan.getDueAmount()) , Double::sum);
        this.totalPaidAmount = this.loans.stream().reduce(0D,
                (partialAmount, loan)->partialAmount + (ObjectUtils.isEmpty(loan.getPaidAmount()) ? 0 : loan.getPaidAmount()), Double::sum);
        log.info("Total amounts updated: totalAmount={}, totalDueAmount={}, totalPaidAmount={}", totalAmount, totalDueAmount, totalPaidAmount);
    }

    private LoanDetailsResponse.LoanSummary getLoanSummary(LoanDetailsResponse lmsLoanDetails) {
        return !ObjectUtils.isEmpty(lmsLoanDetails) ? lmsLoanDetails.getLoanSummary() : null;
    }

    private void updateFromLoanSummary(LendingMerchantLoansResponseDTO.Loan loan, LoanDetailsResponse.LoanSummary loanSummary, LendingPaymentSchedule lpsTable) {
        loan.setEdiAmount(Math.ceil(safeBigDecimalToDouble(loanSummary.getInstalmentAmount())));
        loan.setDueAmount(Math.ceil(safeBigDecimalToDouble(loanSummary.getOverdueInstalmentAmount())));
        loan.setPendingAmount(Math.ceil(safeBigDecimalToDouble(loanSummary.getPendingInstalmentAmount())));
        loan.setPaidPrinciple((double) safeBigDecimalToInt(loanSummary.getPaidPrincipalAmount()));
        loan.setPaidAmount((double) safeBigDecimalToInt(loanSummary.getTotalPaidAmount()));
        loan.setDuePenalty(Math.ceil(safeBigDecimalToDouble(loanSummary.getOverdueOtherCharges())));
    }

    private double calculateRepaymentAmount(LoanDetailsResponse.LoanSummary loanSummary) {
        return Double.sum(loanSummary.getLoanAmount(),
                Double.sum(loanSummary.getPendingInterest().doubleValue(),
                        loanSummary.getOverdueInterest().doubleValue()));
    }
}
