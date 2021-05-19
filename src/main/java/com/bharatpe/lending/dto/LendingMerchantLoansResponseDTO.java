package com.bharatpe.lending.dto;

import java.util.List;
import java.util.stream.Collectors;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class LendingMerchantLoansResponseDTO {

    private List<Loan> loans;

    private boolean success = true;

    private List<LoanEligibilityDTO> eligibility;

    private String message = "success";

    private Boolean topup;

    private Double totalPaidAmount = 0D;
    private Double totalAmount = 0D;
    private Double totalDueAmount = 0D;
    private HalfLoan halfLoan;
    private IOLoan ioLoan;


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
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)

    public class Loan {

        private Long loanId;
        private Double loanAmount;
        private Double ediAmount;
        private Double dueAmount;
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
        private Double paidAmount;
        private Double lastEdiPaid;
        private Double repaymentAmount;
        private Integer ediCount;
        private String lender;
        @JsonProperty(value = "showPaynow")
        private boolean showPaynow = true;
        @JsonProperty(value = "showCustomAmount")
        private boolean showCustomAmount = false;

        public Loan() {
        }

        public Loan(Long loanId, Double loanAmount, Double ediAmount, Double dueAmount, Double interestRate,
                Double processingFee, Double disbursedAmount, Double pendingAmount, Double paidPrinciple, String tenure,
                String startDate, String endDate, String loanType, String status, Double paidAmount,Double repaymentAmount,Integer ediCount, String lender) {
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
        }

        public Long getLoanId() {
            return this.loanId;
        }

        public void setLoanId(Long loanId) {
            this.loanId = loanId;
        }

        public Double getLoanAmount() {
            return this.loanAmount;
        }

        public void setLoanAmount(Double loanAmount) {
            this.loanAmount = loanAmount;
        }

        public Double getEdiAmount() {
            return this.ediAmount;
        }

        public void setEdiAmount(Double ediAmount) {
            this.ediAmount = ediAmount;
        }

        public Double getDueAmount() {
            return this.dueAmount;
        }

        public void setDueAmount(Double dueAmount) {
            this.dueAmount = dueAmount;
        }

        public Double getInterestRate() {
            return this.interestRate;
        }

        public void setInterestRate(Double interestRate) {
            this.interestRate = interestRate;
        }

        public Double getProcessingFee() {
            return this.processingFee;
        }

        public void setProcessingFee(Double processingFee) {
            this.processingFee = processingFee;
        }

        public Double getDisbursedAmount() {
            return this.disbursedAmount;
        }

        public void setDisbursedAmount(Double disbursedAmount) {
            this.disbursedAmount = disbursedAmount;
        }

        public Double getPendingAmount() {
            return this.pendingAmount;
        }

        public void setPendingAmount(Double pendingAmount) {
            this.pendingAmount = pendingAmount;
        }

        public Double getPaidPrinciple() {
            return this.paidPrinciple;
        }

        public void setPaidPrinciple(Double paidPrinciple) {
            this.paidPrinciple = paidPrinciple;
        }

        public String getTenure() {
            return this.tenure;
        }

        public void setTenure(String tenure) {
            this.tenure = tenure;
        }

        public String getStartDate() {
            return this.startDate;
        }

        public void setStartDate(String startDate) {
            this.startDate = startDate;
        }

        public String getEndDate() {
            return this.endDate;
        }

        public void setEndDate(String endDate) {
            this.endDate = endDate;
        }

        public String getLoanType() {
            return this.loanType;
        }

        public void setLoanType(String loanType) {
            this.loanType = loanType;
        }

        public String getStatus() {
            return this.status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Double getPaidAmount() {
            return paidAmount;
        }

        public void setPaidAmount(Double paidAmount) {
            this.paidAmount = paidAmount;
        }

        public Double getLastEdiPaid() {
            return lastEdiPaid;
        }

        public void setLastEdiPaid(Double lastEdiPaid) {
            this.lastEdiPaid = lastEdiPaid;
        }

        public boolean isShowPaynow() {
            return showPaynow;
        }

        public void setShowPaynow(boolean showPaynow) {
            this.showPaynow = showPaynow;
        }

        public boolean isShowCustomAmount() {
            return showCustomAmount;
        }

        public void setShowCustomAmount(boolean showCustomAmount) {
            this.showCustomAmount = showCustomAmount;
        }

        public Double getRepaymentAmount() {
            return repaymentAmount;
        }

        public void setRepaymentAmount(Double repaymentAmount) {
            this.repaymentAmount = repaymentAmount;
        }

        public Integer getEdiCount() {
            return ediCount;
        }

        public void setEdiCount(Integer ediCount) {
            this.ediCount = ediCount;
        }


        public String getLender() {
            return lender;
        }

        public void setLender(String lender) {
            this.lender = lender;
        }

        public Loan loanId(Long loanId) {
            this.loanId = loanId;
            return this;
        }

        public Loan loanAmount(Double loanAmount) {
            this.loanAmount = loanAmount;
            return this;
        }

        public Loan ediAmount(Double ediAmount) {
            this.ediAmount = ediAmount;
            return this;
        }

        public Loan dueAmount(Double dueAmount) {
            this.dueAmount = dueAmount;
            return this;
        }

        public Loan interestRate(Double interestRate) {
            this.interestRate = interestRate;
            return this;
        }

        public Loan processingFee(Double processingFee) {
            this.processingFee = processingFee;
            return this;
        }

        public Loan disbursedAmount(Double disbursedAmount) {
            this.disbursedAmount = disbursedAmount;
            return this;
        }

        public Loan pendingAmount(Double pendingAmount) {
            this.pendingAmount = pendingAmount;
            return this;
        }

        public Loan paidPrinciple(Double paidPrinciple) {
            this.paidPrinciple = paidPrinciple;
            return this;
        }

        public Loan tenure(String tenure) {
            this.tenure = tenure;
            return this;
        }

        public Loan startDate(String startDate) {
            this.startDate = startDate;
            return this;
        }

        public Loan endDate(String endDate) {
            this.endDate = endDate;
            return this;
        }

        public Loan loanType(String loanType) {
            this.loanType = loanType;
            return this;
        }

        public Loan status(String status) {
            this.status = status;
            return this;
        }



        @Override
        public String toString() {
            return "{" + " loanId='" + getLoanId() + "'" + ", loanAmount='" + getLoanAmount() + "'" + ", ediAmount='"
                    + getEdiAmount() + "'" + ", dueAmount='" + getDueAmount() + "'" + ", interestRate='"
                    + getInterestRate() + "'" + ", processingFee='" + getProcessingFee() + "'" + ", disbursedAmount='"
                    + getDisbursedAmount() + "'" + ", pendingAmount='" + getPendingAmount() + "'" + ", paidPrinciple='"
                    + getPaidPrinciple() + "'" + ", tenure='" + getTenure() + "'" + ", startDate='" + getStartDate()
                    + "'" + ", endDate='" + getEndDate() + "'" + ", loanType='" + getLoanType() + "'" + ", status='"
                    + getStatus() + "'" + " lender='" + getLender() + "'" + "}";
        }

    }

    public List<Loan> getLoans() {
        return loans;
    }

    public void setLoans(List<Loan> loans) {
        this.loans = loans;
    }

    public void setLoansFromLendingPaymentSchedule(List<LendingPaymentSchedule> loansList) {
        this.loans = loansList.stream().map(this::lendingPaymentScheduleToLoan).collect(Collectors.toList());
        this.totalAmount = this.loans.stream().reduce(0D,(partialAmount, loan)->partialAmount+loan.getLoanAmount(), Double::sum);
        this.totalDueAmount = this.loans.stream().reduce(0D,(partialAmount, loan)->partialAmount+loan.getDueAmount(), Double::sum);
        this.totalPaidAmount = this.loans.stream().reduce(0D,(partialAmount, loan)->partialAmount+loan.getPaidAmount(), Double::sum);
    }

    private Loan lendingPaymentScheduleToLoan(LendingPaymentSchedule lendingPaymentSchedule) {
        LendingApplication application = lendingPaymentSchedule.getLoanApplication();
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
        Double pendingAmount = loanAmount - paidPrinciple + dueInterest;
        String lender = lendingPaymentSchedule.getNbfc();
        return new Loan(lendingPaymentSchedule.getId(), loanAmount, ediAmount, dueAmount, interestRate, processingFee,
                disbursedAmount, pendingAmount, paidPrinciple, tenure, startDate, endDate, loanType, status, lendingPaymentSchedule.getPaidAmount(),lendingPaymentSchedule.getTotalPayableAmount(),lendingPaymentSchedule.getEdiCount(), lender);
    }

    public boolean getSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Double getTotalPaidAmount() {
        return totalPaidAmount;
    }

    public void setTotalPaidAmount(Double totalPaidAmount) {
        this.totalPaidAmount = totalPaidAmount;
    }

    public Double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Double getTotalDueAmount() {
        return totalDueAmount;
    }

    public void setTotalDueAmount(Double totalDueAmount) {
        this.totalDueAmount = totalDueAmount;
    }

    public boolean isSuccess() {
        return success;
    }

    public Boolean getTopup() {
        return topup;
    }

    public void setTopup(Boolean topup) {
        this.topup = topup;
    }

    public List<LoanEligibilityDTO> getEligibility() {
        return eligibility;
    }

    public void setEligibility(List<LoanEligibilityDTO> eligibility) {
        this.eligibility = eligibility;
    }

    public HalfLoan getHalfLoan() {
        return halfLoan;
    }

    public void setHalfLoan(HalfLoan halfLoan) {
        this.halfLoan = halfLoan;
    }

    public IOLoan getIoLoan() {
        return ioLoan;
    }

    public void setIoLoan(IOLoan ioLoan) {
        this.ioLoan = ioLoan;
    }

    @Override
    public String toString() {
        return "LendingMerchantLoansResponseDTO{" +
                "loans=" + loans +
                ", success=" + success +
                ", eligibility=" + eligibility +
                ", message='" + message + '\'' +
                ", topup=" + topup +
                ", totalPaidAmount=" + totalPaidAmount +
                ", totalAmount=" + totalAmount +
                ", totalDueAmount=" + totalDueAmount +
                ", halfLoan=" + halfLoan +
                ", ioLoan=" + ioLoan +
                '}';
    }
}
