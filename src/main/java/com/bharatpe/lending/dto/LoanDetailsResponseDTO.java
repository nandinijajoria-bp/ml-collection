package com.bharatpe.lending.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LoanDetailsResponseDTO {
	
	private boolean success;
	private String message;
	private String deeplink;
	private LoanDetailsDTO details;
	
	public String getDeeplink() {
		return deeplink;
	}

	public void setDeeplink(String deeplink) {
		this.deeplink = deeplink;
	}

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
		return "LoanDetailsResponseDTO [success=" + success + ", message=" + message + ", deeplink=" + deeplink
				+ ", details=" + details + "]";
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
		private boolean accountDetails = false;
		private boolean skipEnatch = false;
		@JsonProperty(value = "isZomato")
		private boolean isZomato = false;
		private String enach;
		private String city;
		private Integer pincode;
		private String tempClosed;
		private Boolean bharatSwipe=false;
		private  Double bharatSwipeAmount;
		private boolean activeLoan = false;
		private boolean syncContacts = false;
		private boolean hasExperian = false;
		private Double bureauScore;


		@JsonProperty(value = "loan_history")
		private List<LoanHistoryDTO> history;
		
		private List<LoanEligibilityDTO> eligibility;
		
		@JsonProperty(value = "topup_loan")
		private List<LoanEligibilityDTO> topupLoan;
		
		@JsonProperty(value = "loan_application")
		private LoanApplicationDTO loanApplication;

		public boolean isAccountDetails() {
			return accountDetails;
		}

		public void setAccountDetails(boolean accountDetails) {
			this.accountDetails = accountDetails;
		}

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

		public List<LoanEligibilityDTO> getTopupLoan() {
			return topupLoan;
		}

		public void setTopupLoan(List<LoanEligibilityDTO> topupLoan) {
			this.topupLoan = topupLoan;
		}

		public boolean isSkipEnatch() {
			return skipEnatch;
		}

		public void setSkipEnatch(boolean skipEnatch) {
			this.skipEnatch = skipEnatch;
		}

		public boolean isZomato() {
			return isZomato;
		}

		public void setZomato(boolean zomato) {
			isZomato = zomato;
		}

		public Boolean getBharatSwipe() {
			return bharatSwipe;
		}

		public void setBharatSwipe(Boolean bharatSwipe) {
			this.bharatSwipe = bharatSwipe;
		}

		public Double getBharatSwipeAmount() {
			return bharatSwipeAmount;
		}

		public void setBharatSwipeAmount(Double bharatSwipeAmount) {
			this.bharatSwipeAmount = bharatSwipeAmount;
		}

		public boolean isActiveLoan() {
			return activeLoan;
		}

		public void setActiveLoan(boolean activeLoan) {
			this.activeLoan = activeLoan;
		}

		public boolean isSyncContacts() {
			return syncContacts;
		}

		public void setSyncContacts(boolean syncContacts) {
			this.syncContacts = syncContacts;
		}

		public boolean isHasExperian() {
			return hasExperian;
		}

		public void setHasExperian(boolean hasExperian) {
			this.hasExperian = hasExperian;
		}

		public Double getBureauScore() {
			return bureauScore;
		}

		public void setBureauScore(Double bureauScore) {
			this.bureauScore = bureauScore;
		}

		@Override
		public String toString() {
			return "LoanDetailsDTO [eligible=" + eligible + ", rejected=" + rejected + ", panCard=" + panCard
					+ ", rejectReason=" + rejectReason + ", loanClosed=" + loanClosed + ", noExperian=" + noExperian
					+ ", maskedMobiles=" + maskedMobiles + ", ogl=" + ogl + ", accountDetails=" + accountDetails
					+ ", skipEnatch=" + skipEnatch + ", isZomato=" + isZomato + ", enach=" + enach + ", city=" + city
					+ ", pincode=" + pincode + ", tempClosed=" + tempClosed + ", bharatSwipe=" + bharatSwipe
					+ ", history=" + history + ", eligibility=" + eligibility + ", topupLoan=" + topupLoan
					+ ", loanApplication=" + loanApplication + "]";
		}

	}
}
