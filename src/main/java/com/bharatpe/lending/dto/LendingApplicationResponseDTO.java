package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class LendingApplicationResponseDTO {

    @JsonProperty("loan_application")
    private LoanApplication loanApplication;
    
    private Boolean success;
    
    private String message;

    private String code;

    public LendingApplicationResponseDTO() {
		super();
	}

	public LendingApplicationResponseDTO(Boolean success, String message) {
		super();
		this.success = success;
		this.message = message;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

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

        @Override
        public String toString() {
            return "LoanApplication{" +
                    "applicationId=" + applicationId +
                    ", applicationStatus='" + applicationStatus + '\'' +
                    ", selectedLoan=" + selectedLoan +
                    ", shopDetails=" + shopDetails +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "LendingApplicationResponse{" +
                "loanApplication=" + loanApplication +
                ", success=" + success +
                '}';
    }
}
