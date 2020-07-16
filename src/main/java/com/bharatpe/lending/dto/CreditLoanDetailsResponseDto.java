package com.bharatpe.lending.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CreditLoanDetailsResponseDto {
	
	private boolean success=true;
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
		return "CreditLoanDetailsResponseDto [success=" + success + ", message=" + message + ", details=" + details
				+ "]";
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
		private boolean skipEnatch = true;
		private String enach;
		private String loanPage;
		private String city;
		private Integer pincode;
		private String tempClosed;
		private Float creditAmount;
		private Float amount;
		private boolean organised;
		private String category;
		private Boolean accountPresent=false;
		
		public Float getAmount() {
			return amount;
		}

		public void setAmount(Float amount) {
			this.amount = amount;
		}

		@JsonProperty(value = "loan_application")
		private LoanApplicationDTO loanApplication;

		public Boolean getAccountPresent() {
			return accountPresent;
		}

		public void setAccountPresent(Boolean accountPresent) {
			this.accountPresent = accountPresent;
		}

		public String getCategory() {
			return category;
		}

		public void setCategory(String category) {
			this.category = category;
		}

		public boolean isOrganised() {
			return organised;
		}

		public void setOrganised(boolean organised) {
			this.organised = organised;
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

		public boolean isLoanClosed() {
			return loanClosed;
		}

		public void setLoanClosed(boolean loanClosed) {
			this.loanClosed = loanClosed;
		}

		public boolean isNoExperian() {
			return noExperian;
		}

		public void setNoExperian(boolean noExperian) {
			this.noExperian = noExperian;
		}

		public List<String> getMaskedMobiles() {
			return maskedMobiles;
		}

		public void setMaskedMobiles(List<String> maskedMobiles) {
			this.maskedMobiles = maskedMobiles;
		}

		public boolean isOgl() {
			return ogl;
		}

		public void setOgl(boolean ogl) {
			this.ogl = ogl;
		}

		public boolean isAccountDetails() {
			return accountDetails;
		}

		public void setAccountDetails(boolean accountDetails) {
			this.accountDetails = accountDetails;
		}

		public boolean isSkipEnatch() {
			return skipEnatch;
		}

		public void setSkipEnatch(boolean skipEnatch) {
			this.skipEnatch = skipEnatch;
		}

		public String getEnach() {
			return enach;
		}

		public void setEnach(String enach) {
			this.enach = enach;
		}

		public String getLoanPage() {
			return loanPage;
		}

		public void setLoanPage(String loanPage) {
			this.loanPage = loanPage;
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

		public String getTempClosed() {
			return tempClosed;
		}

		public void setTempClosed(String tempClosed) {
			this.tempClosed = tempClosed;
		}

		public Float getCreditAmount() {
			return creditAmount;
		}

		public void setCreditAmount(Float creditAmount) {
			this.creditAmount = creditAmount;
		}

		public LoanApplicationDTO getLoanApplication() {
			return loanApplication;
		}

		public void setLoanApplication(LoanApplicationDTO loanApplication) {
			this.loanApplication = loanApplication;
		}

		@Override
		public String toString() {
			return "LoanDetailsDTO [eligible=" + eligible + ", rejected=" + rejected + ", panCard=" + panCard
					+ ", rejectReason=" + rejectReason + ", loanClosed=" + loanClosed + ", noExperian=" + noExperian
					+ ", maskedMobiles=" + maskedMobiles + ", ogl=" + ogl + ", accountDetails=" + accountDetails
					+ ", skipEnatch=" + skipEnatch + ", enach=" + enach + ", loanPage=" + loanPage + ", city=" + city
					+ ", pincode=" + pincode + ", tempClosed=" + tempClosed + ", creditAmount=" + creditAmount
					+ ", organised=" + organised + ", category=" + category + ", loanApplication=" + loanApplication
					+ "]";
		}

	}
}
