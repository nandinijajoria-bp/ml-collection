package com.bharatpe.lending.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LoanApplicationDTO {

	@JsonProperty(value = "shop_details")
	private ShopDetailsDTO shopDetails;
	
	@JsonProperty(value = "selected_loan")
	private SelectedLoanDTO selectedLoan;
	
	private List<DocumentDTO> documents;
	
	@JsonProperty(value = "application_status")
	private String applicationStatus = "";
	
	@JsonProperty(value = "application_id")
	private String applicationId;
	
	@JsonProperty(value = "status_title")
	private String statusTitle = "";
	
	@JsonProperty(value = "status_message")
	private String statusMessage = "";
	
	@JsonProperty(value = "agreement")
	private String agreement = "";

	@JsonProperty(value = "reapply")
	private boolean showReapply;

	@JsonProperty(value = "self_verification")
	private boolean selfVerification = true;

	public boolean isSelfVerification() {
		return selfVerification;
	}

	public void setSelfVerification(boolean selfVerification) {
		this.selfVerification = selfVerification;
	}

	public ShopDetailsDTO getShopDetails() {
		return shopDetails;
	}

	public void setShopDetails(ShopDetailsDTO shopDetails) {
		this.shopDetails = shopDetails;
	}

	public SelectedLoanDTO getSelectedLoan() {
		return selectedLoan;
	}

	public void setSelectedLoan(SelectedLoanDTO selectedLoan) {
		this.selectedLoan = selectedLoan;
	}

	public List<DocumentDTO> getDocuments() {
		return documents;
	}

	public void setDocuments(List<DocumentDTO> documents) {
		this.documents = documents;
	}

	public String getApplicationStatus() {
		return applicationStatus;
	}

	public void setApplicationStatus(String applicationStatus) {
		this.applicationStatus = applicationStatus;
	}

	public String getApplicationId() {
		return applicationId;
	}

	public void setApplicationId(String applicationId) {
		this.applicationId = applicationId;
	}

	public String getStatusTitle() {
		return statusTitle;
	}

	public void setStatusTitle(String statusTitle) {
		this.statusTitle = statusTitle;
	}

	public String getStatusMessage() {
		return statusMessage;
	}

	public void setStatusMessage(String statusMessage) {
		this.statusMessage = statusMessage;
	}

	public String getAgreement() {
		return agreement;
	}

	public void setAgreement(String agreement) {
		this.agreement = agreement;
	}
	
	public boolean isShowReapply() {
		return showReapply;
	}

	public void setShowReapply(boolean showReapply) {
		this.showReapply = showReapply;
	}

	@Override
	public String toString() {
		return "LoanApplicationDTO [shopDetails=" + shopDetails + ", selectedLoan=" + selectedLoan + ", documents="
				+ documents + ", applicationStatus=" + applicationStatus + ", applicationId=" + applicationId
				+ ", statusTitle=" + statusTitle + ", statusMessage=" + statusMessage + ", agreement=" + agreement
				+ "]";
	}
	
}
