package com.bharatpe.lending.dto;

import java.util.List;
import java.util.stream.Collectors;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class LendingActiveLoansResponseDTO {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<ActiveLoan> activeLoans;

    private boolean success = true;

    private String message = "success";

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class ActiveLoan {
        private Long loanId;
        private Double loanAmount;
        private Double ediAmount;
        private Double dueAmount;
        private String startDate;
        private String endDate;
        private String loanType;
        private Integer dpd;
        public ActiveLoan() {
        }

        public ActiveLoan(Long loanId, Double loanAmount, Double ediAmount, Double dueAmount, String startDate,
                String endDate, String loanType, Integer dpd) {
            this.loanId = loanId;
            this.loanAmount = loanAmount;
            this.ediAmount = ediAmount;
            this.dueAmount = dueAmount;
            this.startDate = startDate;
            this.endDate = endDate;
            this.loanType = loanType;
            this.dpd = dpd;
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
            return loanType;
        }

        public void setLoanType(String loanType) {
            this.loanType = loanType;
        }

        public Integer getDpd() {
            return dpd;
        }

        public void setDpd(Integer dpd) {
            this.dpd = dpd;
        }

        @Override
        public String toString() {
            return "{" + " loanId='" + getLoanId() + "'" + ", loanAmount='" + getLoanAmount() + "'" + ", ediAmount='"
                    + getEdiAmount() + "'" + ", dueAmount='" + getDueAmount() + "'" + ", startDate='" + getStartDate()
                    + "'" + ", endDate='" + getEndDate() + "'" + "}";
        }
    }

    public List<ActiveLoan> getActiveLoans() {
        return activeLoans;
    }

    public void setActiveLoans(List<ActiveLoan> activeLoans) {
        this.activeLoans = activeLoans;
    }

    public void setActiveLoansFromLendingPaymentSchedule(List<LendingPaymentSchedule> activeLoansList) {
        this.activeLoans = activeLoansList.stream().map(this::lendingPaymentScheduleToActiveLoan)
                .collect(Collectors.toList());
    }

    private ActiveLoan lendingPaymentScheduleToActiveLoan(LendingPaymentSchedule lendingPaymentSchedule) {
        Double loanAmount = lendingPaymentSchedule.getLoanAmount() != null ? lendingPaymentSchedule.getLoanAmount() : 0d;
        Double ediAmount = lendingPaymentSchedule.getEdiAmount() != null ? lendingPaymentSchedule.getEdiAmount() : 0d;
        Double dueAmount = lendingPaymentSchedule.getDueAmount() != null ? lendingPaymentSchedule.getDueAmount() : 0d;
        String startDate = lendingPaymentSchedule.getStartDate() != null ? lendingPaymentSchedule.getStartDate().toString() : "";
        String endDate = lendingPaymentSchedule.getTentativeClosingDate() != null ? lendingPaymentSchedule.getTentativeClosingDate().toString() : "";
        String loanType = lendingPaymentSchedule.getLoanApplication() != null ? lendingPaymentSchedule.getLoanApplication().getLoanType() : "";
        Integer dpd = (int) (lendingPaymentSchedule.getDueAmount() / lendingPaymentSchedule.getEdiAmount());
        return new ActiveLoan(lendingPaymentSchedule.getId(), loanAmount, ediAmount, dueAmount, startDate, endDate, loanType, dpd);
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

    @Override
    public String toString() {
        return "LendingActiveLoansResponseDTO{" + "active_loans=" + activeLoans + ", success='" + success + '\''
                + ", message='" + message + '\'' + '}';
    }
}
