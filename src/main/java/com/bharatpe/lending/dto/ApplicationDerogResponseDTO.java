package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class ApplicationDerogResponseDTO {

    private boolean success;

    private String message;

    private String manualCibil;

    private String manualCibilReason;

    private boolean isRejected;

	public ApplicationDerogResponseDTO() {
	}

	public ApplicationDerogResponseDTO(boolean success, String message, String manualCibil, String manualCibilReason, boolean isRejected) {
		this.success = success;
		this.message = message;
		this.manualCibil = manualCibil;
		this.manualCibilReason = manualCibilReason;
		this.isRejected = isRejected;
	}

	public boolean isSuccess() {
		return this.success;
	}

	public boolean getSuccess() {
		return this.success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public String getMessage() {
		return this.message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getManualCibil() {
		return this.manualCibil;
	}

	public void setManualCibil(String manualCibil) {
		this.manualCibil = manualCibil;
	}

	public String getManualCibilReason() {
		return this.manualCibilReason;
	}

	public void setManualCibilReason(String manualCibilReason) {
		this.manualCibilReason = manualCibilReason;
	}

	public boolean getIsRejected() {
		return this.isRejected;
	}

	public void setIsRejected(boolean isRejected) {
		this.isRejected = isRejected;
	}

	@Override
	public String toString() {
		return "ApplicationDerogResponseDTO{" +
			" success='" + isSuccess() + "'" +
			", message='" + getMessage() + "'" +
			", manualCibil='" + getManualCibil() + "'" +
			", manualCibilReason='" + getManualCibilReason() + "'" +
			", isRejected='" + getIsRejected() + "'" +
			"}";
	}

}
