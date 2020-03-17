package com.bharatpe.lending.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LoanDetailsResponseDTO {
	
	private boolean success;
	private String message;
	private LoanDetailsDTO details;

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public LoanDetailsDTO getDetails() {
		return details;
	}

	public void setDetails(LoanDetailsDTO details) {
		this.details = details;
	}

	@Override
	public String toString() {
		return "LoanDetailsResponseDTO [success=" + success + ", details=" + details + "]";
	}

	public static class LoanDetailsDTO {
		private boolean eligible;
		private boolean rejected;
		private String panCard;
		private String rejectReason;
		private boolean loanClosed = false;
		private boolean noExperian = false;
		private List<String> maskedMobiles;
		private boolean ogl = false;
		private String enach;
		private String city;
		private Integer pincode;
		private String tempClosed;

		@JsonProperty(value = "loan_history")
		private List<LoanHistoryDTO> history;
		
		private List<LoanEligibilityDTO> eligibility;
		
		@JsonProperty(value = "loan_application")
		private LoanApplicationDTO loanApplication;

		public String getTempClosed() {
			return tempClosed;
		}

		public void setTempClosed(String tempClosed) {
			this.tempClosed = tempClosed;
		}

		public String getCity() {
			return city;
		}

		public void setCity(String city) {
			this.city = city;
		}

		public Integer getPincode() {
			return pincode;
		}

		public void setPincode(Integer pincode) {
			this.pincode = pincode;
		}

		public List<String> getMaskedMobiles() {
			return maskedMobiles;
		}

		public void setMaskedMobiles(List<String> maskedMobiles) {
			this.maskedMobiles = maskedMobiles;
		}

		public boolean isLoanClosed() {
			return loanClosed;
		}

		public void setLoanClosed(boolean loanClosed) {
			this.loanClosed = loanClosed;
		}

		public boolean isEligible() {
			return eligible;
		}

		public void setEligible(boolean eligible) {
			this.eligible = eligible;
		}

		public boolean isRejected() {
			return rejected;
		}

		public void setRejected(boolean rejected) {
			this.rejected = rejected;
		}

		public String getPanCard() {
			return panCard;
		}

		public void setPanCard(String panCard) {
			this.panCard = panCard;
		}

		public String getRejectReason() {
			return rejectReason;
		}

		public void setRejectReason(String rejectReason) {
			this.rejectReason = rejectReason;
		}

		public List<LoanHistoryDTO> getHistory() {
			return history;
		}

		public void setHistory(List<LoanHistoryDTO> history) {
			this.history = history;
		}

		public List<LoanEligibilityDTO> getEligibility() {
			return eligibility;
		}

		public void setEligibility(List<LoanEligibilityDTO> eligibility) {
			this.eligibility = eligibility;
		}

		public LoanApplicationDTO getLoanApplication() {
			return loanApplication;
		}

		public void setLoanApplication(LoanApplicationDTO loanApplication) {
			this.loanApplication = loanApplication;
		}

		public boolean isNoExperian() {
			return noExperian;
		}

		public void setNoExperian(boolean noExperian) {
			this.noExperian = noExperian;
		}

		public boolean isOgl() {
			return ogl;
		}

		public void setOgl(boolean ogl) {
			this.ogl = ogl;
		}

		public String getEnach() {
			return enach;
		}

		public void setEnach(String enach) {
			this.enach = enach;
		}

		@Override
		public String toString() {
			return "LoanDetailsDTO{" +
					"eligible=" + eligible +
					", rejected=" + rejected +
					", panCard='" + panCard + '\'' +
					", rejectReason='" + rejectReason + '\'' +
					", loanClosed=" + loanClosed +
					", noExperian=" + noExperian +
					", maskedMobiles=" + maskedMobiles +
					", ogl=" + ogl +
					", history=" + history +
					", eligibility=" + eligibility +
					", loanApplication=" + loanApplication +
					", enach=" + enach +
					'}';
		}
	}
}
