package com.bharatpe.lending.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class LiquiloanCallbackRequestDTO {
	
	private String applicationId;
	private String nbfcId;

	public String getApplicationId() {
		return applicationId;
	}

	public void setApplicationId(String applicationId) {
		this.applicationId = applicationId;
	}

	public String getNbfcId() {
		return nbfcId;
	}

	public void setNbfcId(String nbfcId) {
		this.nbfcId = nbfcId;
	}

	@Override
	public String toString() {
		return "LiquiloanCallbackRequestDTO{" +
				"applicationId=" + applicationId +
				", nbfcId='" + nbfcId + '\'' +
				'}';
	}
}
