package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class PaymentDetailsResponseDTO {

	private boolean success;
	private String message;
	private Data data;
	
	public PaymentDetailsResponseDTO() {
		
	}
	
	public PaymentDetailsResponseDTO(String message) {
		this.message = message;
	}
	
	public PaymentDetailsResponseDTO(PaymentDetailsResponseDTO.Data data) {
		this.data = data;
		this.success = true;
	}
	
	public boolean isSuccess() {
		return success;
	}
	public void setSuccess(boolean success) {
		this.success = success;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public Data getData() {
		return data;
	}
	public void setData(Data data) {
		this.data = data;
	}

	@Override
	public String toString() {
		return "PaymentDetailsResponseDTO [success=" + success + ", message=" + message + ", data=" + data + "]";
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
	@Getter
	@Setter
	public static class Data { 
		private Integer loanAmount;
		private Integer overdueAmount;
		private Integer principalDueAmount;
		private Double lenderPrincipalDueAmount;
		private Integer overdueDays;
		private Boolean isEligibleForPayment;
		private Integer remainingEdiCount;
		private Double ediAmount;

		private Double paidAmount;

		private Double pendingAmount;
		
		public Data() {
			
		}
		
		public Data(Integer loanAmount, Integer overdueAmount, Integer principalDueAmount, Integer overdueDays, Boolean isEligibleForPayment, Integer remainingEdiCount, Double ediAmount, Double lenderPrincipalDueAmount) {
			this.loanAmount = loanAmount;
			this.overdueAmount = overdueAmount;
			this.principalDueAmount = principalDueAmount;
			this.overdueDays = overdueDays;
			this.isEligibleForPayment = isEligibleForPayment;
			this.remainingEdiCount = remainingEdiCount;
			this.ediAmount = ediAmount;
			this.lenderPrincipalDueAmount = lenderPrincipalDueAmount;
		}

		public Integer getLoanAmount() {
			return loanAmount;
		}
		public void setLoanAmount(Integer loanAmount) {
			this.loanAmount = loanAmount;
		}
		public Integer getOverdueAmount() {
			return overdueAmount;
		}
		public void setOverdueAmount(Integer overdueAmount) {
			this.overdueAmount = overdueAmount;
		}
		public Integer getPrincipalDueAmount() {
			return principalDueAmount;
		}
		public void setPrincipalDueAmount(Integer principalDueAmount) {
			this.principalDueAmount = principalDueAmount;
		}
		public Integer getOverdueDays() {
			return overdueDays;
		}
		public void setOverdueDays(Integer overdueDays) {
			this.overdueDays = overdueDays;
		}
		public Boolean getIsEligibleForPayment() {
			return isEligibleForPayment;
		}
		public void setIsEligibleForPayment(Boolean isEligibleForPayment) {
			this.isEligibleForPayment = isEligibleForPayment;
		}

		public Double getLenderPrincipalDueAmount() {
			return lenderPrincipalDueAmount;
		}

		public void setLenderPrincipalDueAmount(Double lenderPrincipalDueAmount) {
			this.lenderPrincipalDueAmount = lenderPrincipalDueAmount;
		}

		public Boolean getEligibleForPayment() {
			return isEligibleForPayment;
		}

		public void setEligibleForPayment(Boolean eligibleForPayment) {
			isEligibleForPayment = eligibleForPayment;
		}

		public Integer getRemainingEdiCount() {
			return remainingEdiCount;
		}

		public void setRemainingEdiCount(Integer remainingEdiCount) {
			this.remainingEdiCount = remainingEdiCount;
		}

		public Double getEdiAmount() {
			return ediAmount;
		}

		public void setEdiAmount(Double ediAmount) {
			this.ediAmount = ediAmount;
		}

		@Override
		public String toString() {
			return "Data{" +
					"loanAmount=" + loanAmount +
					", overdueAmount=" + overdueAmount +
					", principalDueAmount=" + principalDueAmount +
					", lenderPrincipalDueAmount=" + lenderPrincipalDueAmount +
					", overdueDays=" + overdueDays +
					", isEligibleForPayment=" + isEligibleForPayment +
					", remainingEdiCount=" + remainingEdiCount +
					", ediAmount=" + ediAmount +
					'}';
		}
	}
}