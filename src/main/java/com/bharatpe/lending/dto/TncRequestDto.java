package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TncRequestDto {
	@JsonProperty(value = "amount", required = false)
	private Double amount;
		
	@JsonProperty(value = "tenure", required = false)
	private Integer tenure;
	
	@JsonProperty(value = "application_id", required = false)
	private Long applicationId;
	private String type;
	public Double getAmount() {
		return amount;
	}
	public void setAmount(Double amount) {
		this.amount = amount;
	}
	
	public Integer getTenure() {
		return tenure;
	}
	public void setTenure(Integer tenure) {
		this.tenure = tenure;
	}
	public Long getApplicationId() {
		return applicationId;
	}
	public void setApplicationId(Long applicationId) {
		this.applicationId = applicationId;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}

	@Override
	public String toString() {
		return "TncRequestDto{" +
				"amount=" + amount +
				", tenure=" + tenure +
				", applicationId=" + applicationId +
				", type='" + type + '\'' +
				'}';
	}
}
