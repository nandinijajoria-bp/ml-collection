package com.bharatpe.lending.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.ToString;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@ToString
public class EligibleLendingOffersResponseDTO {

    @JsonInclude(JsonInclude.Include.NON_NULL)

    private boolean success = false;

    private String message;

    private EligibleOfferDetails eligibleOfferDetails;

    public EligibleLendingOffersResponseDTO() {
    }

    public EligibleLendingOffersResponseDTO(boolean success, String message, EligibleOfferDetails eligibleOfferDetails) {
        this.success = success;
        this.message = message;
        this.eligibleOfferDetails = eligibleOfferDetails;
    }

    public EligibleLendingOffersResponseDTO(boolean success, String message, Double amount, List<TenureDetails> tenures) {
        this.success = success;
        this.message = message;
        this.eligibleOfferDetails = new EligibleOfferDetails(amount, tenures);
    }

    public class TenureDetails {

        private String category;
        private String tenure;
        private Integer tenureInMonths;
        private Double rateOfInterest;
        private Integer repaymentAmount;
        private Integer edi;
        private Integer ioedi;
        private Integer financeCharge;
        private Integer ediCount;

        public TenureDetails() {
        }

        public Integer getTenureInMonths() {
            return tenureInMonths;
        }

        public void setTenureInMonths(Integer tenureInMonths) {
            this.tenureInMonths = tenureInMonths;
        }

        public Integer getIoedi() {
            return ioedi;
        }

        public void setIoedi(Integer ioedi) {
            this.ioedi = ioedi;
        }

        public TenureDetails(String category, String tenure, Double rateOfInterest, Integer repaymentAmount, Integer edi, Integer ioedi) {
            this.category = category;
            this.tenure = tenure;
            this.rateOfInterest = rateOfInterest;
            this.repaymentAmount = repaymentAmount;
            this.edi = edi;
            this.ioedi = ioedi;
        }

        public String getCategory() {
            return this.category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getTenure() {
            return this.tenure;
        }

        public void setTenure(String tenure) {
            this.tenure = tenure;
        }

        public Double getRateOfInterest() {
            return this.rateOfInterest;
        }

        public void setRateOfInterest(Double rateOfInterest) {
            this.rateOfInterest = rateOfInterest;
        }

        public Integer getRepaymentAmount() {
            return this.repaymentAmount;
        }

        public void setRepaymentAmount(Integer repaymentAmount) {
            this.repaymentAmount = repaymentAmount;
        }

        public Integer getEdi() {
            return this.edi;
        }

        public void setEdi(Integer edi) {
            this.edi = edi;
        }

        public Integer getIoEdi() {
            return this.ioedi;
        }

        public void setIoEdi(Integer ioedi) {
            this.ioedi = ioedi;
        }

        public Integer getFinanceCharge() {
            return financeCharge;
        }

        public void setFinanceCharge(Integer financeCharge) {
            this.financeCharge = financeCharge;
        }

        public Integer getEdiCount() {
            return ediCount;
        }

        public void setEdiCount(Integer ediCount) {
            this.ediCount = ediCount;
        }

        @Override
        public String toString() {
            return "TenureDetails{" + " tenure='" + getTenure() + "'" + ", rateOfInterest='" + getRateOfInterest() + "'"
                    + ", repaymentAmount='" + getRepaymentAmount() + "'" + ", edi='" + getEdi() + "'" + ", ioedi='" + getIoEdi() + "'" + "}";
        }
    }

    public class EligibleOfferDetails {

        private Double amount;
        private List<TenureDetails> tenures;

        public EligibleOfferDetails() {
        }

        public EligibleOfferDetails(Double amount, List<TenureDetails> tenures) {
            this.amount = amount;
            this.tenures = tenures;
        }

        public Double getAmount() {
            return this.amount;
        }

        public void setAmount(Double amount) {
            this.amount = amount;
        }

        public List<TenureDetails> getTenures() {
            return this.tenures;
        }

        public void setTenures(List<TenureDetails> tenures) {
            this.tenures = tenures;
        }

        public String printTenuresString() {
            return this.tenures.stream().map(e -> e.toString()).reduce(", ", String::concat);
        }

        @Override
        public String toString() {
            return "EligibleOfferDetails{" + " amount='" + getAmount() + "'" + ", tenures= '" + printTenuresString()
                    + "'}";
        }
    }

    public boolean isSuccess() {
        return this.success;
    }

    public boolean getSuccess() {
        return this.success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public EligibleOfferDetails getEligibleOfferDetails() {
        return this.eligibleOfferDetails;
    }

    public void setEligibleOfferDetails(EligibleOfferDetails eligibleOfferDetails) {
        this.eligibleOfferDetails = eligibleOfferDetails;
    }

    @Override
    public String toString() {
        return "EligibleLendingOffersResponseDTO{" + " success='" + isSuccess() + "'" + ", message='" + getMessage()
                + "'" + ", eligibleOfferDetails='" + getEligibleOfferDetails().toString()
                + "'}";
    }
}
