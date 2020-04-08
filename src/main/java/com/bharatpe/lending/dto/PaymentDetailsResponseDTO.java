package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class PaymentDetailsResponseDTO {

	private boolean success = true;
	private String message;
	private Data data = new Data();
	
	public PaymentDetailsResponseDTO() {
		
	}
	
	public PaymentDetailsResponseDTO(String message) {
		
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
	public static class Data { 
		private Integer loanAmount = 100;
		private Integer overdueAmount = 10;
		private Integer principalDueAmount = 10;
		private Integer overdueDays = 2;
		
		public Data() {
			
		}
		
		public Data(Integer loanAmount, Integer overdueAmount, Integer principalDueAmount, Integer overdueDays) {
			this.loanAmount = loanAmount;
			this.overdueAmount = overdueAmount;
			this.principalDueAmount = principalDueAmount;
			this.overdueDays = overdueDays;
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
		@Override
		public String toString() {
			return "Data [loanAmount=" + loanAmount + ", overdueAmount=" + overdueAmount + ", principalDueAmount="
					+ principalDueAmount + ", overdueDays=" + overdueDays + "]";
		}
	}
}