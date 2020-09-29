package com.bharatpe.lending.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class VerifyPanCardDto {
	private Boolean status=true;
	private String message;
	private Boolean isPancardValid;

	public VerifyPanCardDto(Boolean status, String message, Boolean isPancardValid) {
		super();
		this.status = status;
		this.message = message;
		this.isPancardValid = isPancardValid;
	}

	public VerifyPanCardDto() {
		super();
	}

	public Boolean getStatus() {
		return status;
	}

	public void setStatus(Boolean status) {
		this.status = status;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Boolean getIsPancardValid() {
		return isPancardValid;
	}

	public void setIsPancardValid(Boolean isPancardValid) {
		this.isPancardValid = isPancardValid;
	}

	@Override
	public String toString() {
		return "VerifyPanCardDto [status=" + status + ", message=" + message + ", isPancardValid=" + isPancardValid
				+ "]";
	}

}
