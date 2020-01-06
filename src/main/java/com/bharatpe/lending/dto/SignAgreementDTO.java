package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SignAgreementDTO {

	private Boolean agreement;
	
	@JsonProperty(value = "category", required = false)
	private String category;
	
	@JsonProperty(value = "application_id", required = false)
	private Long applicationId;

	public Boolean getAgreement() {
		return agreement;
	}

	public void setAgreement(Boolean agreement) {
		this.agreement = agreement;
	}

	public Long getApplicationId() {
		return applicationId;
	}

	public void setApplicationId(Long applicationId) {
		this.applicationId = applicationId;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	@Override
	public String toString() {
		return "SignAgreementDTO [agreement=" + agreement + ", applicationId=" + applicationId + ", category="
				+ category + "]";
	}
	
}
