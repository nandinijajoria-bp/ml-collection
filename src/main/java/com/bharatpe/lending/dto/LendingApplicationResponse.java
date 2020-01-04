package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class LendingApplicationResponse {

    @JsonProperty("loan_application")
    private LoanApplication loanApplication;

    private Boolean success;

    public LoanApplication getLoanApplication() {
        return loanApplication;
    }

    public void setLoanApplication(LoanApplication loanApplication) {
        this.loanApplication = loanApplication;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public class LoanApplication {
        @JsonProperty("application_id")
        private Long applicationId;

        @JsonProperty("application_status")
        private String applicationStatus;

        @JsonProperty("selected_loan")
        Map<String, Object> selectedLoan;

        @JsonProperty("shop_details")
        Map<String, Object> shopDetails;


        public Long getApplicationId() {
            return applicationId;
        }

        public void setApplicationId(Long applicationId) {
            this.applicationId = applicationId;
        }

        public String getApplicationStatus() {
            return applicationStatus;
        }

        public void setApplicationStatus(String applicationStatus) {
            this.applicationStatus = applicationStatus;
        }

        public Map<String, Object> getSelectedLoan() {
            return selectedLoan;
        }

        public void setSelectedLoan(Map<String, Object> selectedLoan) {
            this.selectedLoan = selectedLoan;
        }

        public Map<String, Object> getShopDetails() {
            return shopDetails;
        }

        public void setShopDetails(Map<String, Object> shopDetails) {
            this.shopDetails = shopDetails;
        }
    }
}
