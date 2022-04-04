package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SignAgreementDTO {

	private Boolean agreement;
	
	@JsonProperty(value = "category", required = false)
	private String category;
	
	@JsonProperty(value = "application_id", required = false)
	private Long applicationId;

	private String appSign;

	@JsonProperty(value = "tenure_in_months")
	private Integer tenureInMonths;

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

	public String getAppSign() {
		return appSign;
	}

	public Integer getTenureInMonths() {
		return tenureInMonths;
	}

	public void setTenureInMonths(Integer tenureInMonths) {
		this.tenureInMonths = tenureInMonths;
	}

	public void setAppSign(String appSign) {
		this.appSign = appSign;
	}

	@Override
	public String toString() {
		return "SignAgreementDTO{" +
				"agreement=" + agreement +
				", category='" + category + '\'' +
				", applicationId=" + applicationId +
				", appSign='" + appSign + '\'' +
				", tenureInMonths=" + tenureInMonths +
				'}';
	}

}
