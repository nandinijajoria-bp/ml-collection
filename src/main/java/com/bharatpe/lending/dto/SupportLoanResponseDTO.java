package com.bharatpe.lending.dto;

import org.joda.time.DateTime;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class SupportLoanResponseDTO {

    private Long merchantId;
    private Boolean activeLoan;
    private Boolean eligible;
    private Boolean experian;
    private Boolean applied;
    private String message;
    private String conditionalMessage;
    private Boolean eNachDone;
    private String applicationStatus;
    private Boolean nachMandatory;
    private String beneficiaryName;
    private String businessName;
    private String city;
    private String mobile;
    private String bankAccount;
    private Boolean creditLineAccount;
    private LoanApplication loanApplication;
    private Eligibility eligibility;
    private List<Map<String, Object>> loanDetailsList;
    private LoanArrangerFee loanArrangerFee;

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public Boolean getActiveLoan() {
        return activeLoan;
    }

    public void setActiveLoan(Boolean activeLoan) {
        this.activeLoan = activeLoan;
    }

    public Boolean getEligible() {
        return eligible;
    }

    public void setEligible(Boolean eligible) {
        this.eligible = eligible;
    }

    public Boolean getExperian() {
        return experian;
    }

    public void setExperian(Boolean experian) {
        this.experian = experian;
    }

    public Boolean getApplied() {
        return applied;
    }

    public void setApplied(Boolean applied) {
        this.applied = applied;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getConditionalMessage() {
        return conditionalMessage;
    }

    public void setConditionalMessage(String conditionalMessage) {
        this.conditionalMessage = conditionalMessage;
    }

    public Boolean geteNachDone() {
        return eNachDone;
    }

    public void seteNachDone(Boolean eNachDone) {
        this.eNachDone = eNachDone;
    }

    public LoanApplication getLoanApplication() {
        return loanApplication;
    }

    public void setLoanApplication(LoanApplication loanApplication) {
        this.loanApplication = loanApplication;
    }

    public String getApplicationStatus() {
        return applicationStatus;
    }

    public void setApplicationStatus(String applicationStatus) {
        this.applicationStatus = applicationStatus;
    }

    public Eligibility getEligibility() {
        return eligibility;
    }

    public void setEligibility(Eligibility eligibility) {
        this.eligibility = eligibility;
    }

    public Boolean getNachMandatory() {
        return nachMandatory;
    }

    public void setNachMandatory(Boolean nachMandatory) {
        this.nachMandatory = nachMandatory;
    }

    public List<Map<String, Object>> getLoanDetailsList() {
        return loanDetailsList;
    }

    public void setLoanDetailsList(List<Map<String, Object>> loanDetailsList) {
        this.loanDetailsList = loanDetailsList;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public void setBeneficiaryName(String beneficiaryName) {
        this.beneficiaryName = beneficiaryName;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getBankAccount() {
        return bankAccount;
    }

    public void setBankAccount(String bankAccount) {
        this.bankAccount = bankAccount;
    }

    public Boolean getCreditLineAccount() {
        return creditLineAccount;
    }

    public void setCreditLineAccount(Boolean creditLineAccount) {
        this.creditLineAccount = creditLineAccount;
    }

    public LoanArrangerFee getLoanArrangerFee() {
        return loanArrangerFee;
    }

    public void setLoanArrangerFee(LoanArrangerFee loanArrangerFee) {
        this.loanArrangerFee = loanArrangerFee;
    }

    @Override
    public String toString() {
        return "SupportLoanResponseDTO{" +
            "merchantId=" + merchantId +
            ", activeLoan=" + activeLoan +
            ", eligible=" + eligible +
            ", experian=" + experian +
            ", applied=" + applied +
            ", message='" + message + '\'' +
            ", conditionalMessage='" + conditionalMessage + '\'' +
            ", eNachDone=" + eNachDone +
            ", applicationStatus='" + applicationStatus + '\'' +
            ", nachMandatory=" + nachMandatory +
            ", beneficiaryName='" + beneficiaryName + '\'' +
            ", businessName='" + businessName + '\'' +
            ", city='" + city + '\'' +
            ", mobile='" + mobile + '\'' +
            ", bankAccount='" + bankAccount + '\'' +
            ", creditLineAccount=" + creditLineAccount +
            ", loanApplication=" + loanApplication +
            ", eligibility=" + eligibility +
            ", loanDetailsList=" + loanDetailsList +
            ", loanArrangerFee=" + loanArrangerFee +
            '}';
    }

    public static class Eligibility {
        private Double loanAmount;
        private Integer ediAmount;
        private String tenure;
        private Integer repayment;

        public Double getLoanAmount() {
            return loanAmount;
        }

        public void setLoanAmount(Double loanAmount) {
            this.loanAmount = loanAmount;
        }

        public Integer getEdiAmount() {
            return ediAmount;
        }

        public void setEdiAmount(Integer ediAmount) {
            this.ediAmount = ediAmount;
        }

        public String getTenure() {
            return tenure;
        }

        public void setTenure(String tenure) {
            this.tenure = tenure;
        }

        public Integer getRepayment() {
            return repayment;
        }

        public void setRepayment(Integer repayment) {
            this.repayment = repayment;
        }

        @Override
        public String toString() {
            return "Eligibility{" +
                "loanAmount=" + loanAmount +
                ", ediAmount=" + ediAmount +
                ", tenure='" + tenure + '\'' +
                ", repayment=" + repayment +
                '}';
        }
    }

    public static class LoanApplication {
        private Date applicationSubmittedDate;
        private String loanId;
        private Double loanAmount;
        private Double ediAmount;
        private String tenure;
        private Double interestRate;
        private Double repayment;

        public Date getApplicationSubmittedDate() {
            return applicationSubmittedDate;
        }

        public void setApplicationSubmittedDate(Date applicationSubmittedDate) {
            this.applicationSubmittedDate = applicationSubmittedDate;
        }

        public String getLoanId() {
            return loanId;
        }

        public void setLoanId(String loanId) {
            this.loanId = loanId;
        }

        public Double getLoanAmount() {
            return loanAmount;
        }

        public void setLoanAmount(Double loanAmount) {
            this.loanAmount = loanAmount;
        }

        public Double getEdiAmount() {
            return ediAmount;
        }

        public void setEdiAmount(Double ediAmount) {
            this.ediAmount = ediAmount;
        }

        public String getTenure() {
            return tenure;
        }

        public void setTenure(String tenure) {
            this.tenure = tenure;
        }

        public Double getInterestRate() {
            return interestRate;
        }

        public void setInterestRate(Double interestRate) {
            this.interestRate = interestRate;
        }

        public Double getRepayment() {
            return repayment;
        }

        public void setRepayment(Double repayment) {
            this.repayment = repayment;
        }

        @Override
        public String toString() {
            return "LoanApplication{" +
                "applicationSubmittedDate=" + applicationSubmittedDate +
                ", loanId=" + loanId +
                ", loanAmount=" + loanAmount +
                ", ediAmount=" + ediAmount +
                ", tenure='" + tenure + '\'' +
                ", interestRate=" + interestRate +
                ", repayment=" + repayment +
                '}';
        }
    }

    public static class LoanArrangerFee {
        private Double feeAmount;
        private Boolean arrangerFeeRefundEligible;
        private Boolean arrangerFeeRefunded;
        private Date timestamp;

        public Double getFeeAmount() {
            return feeAmount;
        }

        public void setFeeAmount(Double feeAmount) {
            this.feeAmount = feeAmount;
        }

        public Boolean getArrangerFeeRefundEligible() {
            return arrangerFeeRefundEligible;
        }

        public void setArrangerFeeRefundEligible(Boolean arrangerFeeRefundEligible) {
            this.arrangerFeeRefundEligible = arrangerFeeRefundEligible;
        }

        public Boolean getArrangerFeeRefunded() {
            return arrangerFeeRefunded;
        }

        public void setArrangerFeeRefunded(Boolean arrangerFeeRefunded) {
            this.arrangerFeeRefunded = arrangerFeeRefunded;
        }

        public Date getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Date timestamp) {
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return "LoanArrangerFee{" +
                "feeAmount=" + feeAmount +
                ", arrangerFeeRefundEligible=" + arrangerFeeRefundEligible +
                ", arrangerFeeRefunded=" + arrangerFeeRefunded +
                ", timestamp=" + timestamp +
                '}';
        }
    }
}
