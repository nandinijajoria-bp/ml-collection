package com.bharatpe.lending.dto;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class DailySettlementResponseDto {
	
	private Boolean success=true;
	private String message="";
	private Double totalAmount=0D;
	private Double totalEdi=0D;
	private Double totalMad=0D;
	
	private List<DailyRepayment> repayments;
	
	public List<DailyRepayment> getRepayments() {
		return repayments;
	}
	public void setRepayments(List<DailyRepayment> repayments) {
		this.repayments = repayments;
	}
	
	public Double getTotalEdi() {
		return totalEdi;
	}
	public void setTotalEdi(Double totalEdi) {
		this.totalEdi = totalEdi;
	}
	public Double getTotalMad() {
		return totalMad;
	}
	public void setTotalMad(Double totalMad) {
		this.totalMad = totalMad;
	}
	public Boolean getSuccess() {
		return success;
	}
	public void setSuccess(Boolean success) {
		this.success = success;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public Double getTotalAmount() {
		return totalAmount;
	}
	public void setTotalAmount(Double totalAmount) {
		this.totalAmount = totalAmount;
	}
	
	@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
	@JsonInclude(JsonInclude.Include.NON_NULL)
	
	public static class DailyRepayment{
			
			private Date date;
			private Double repaymentAmount;
			private String mode;
			private String tenure;
			private String loanType;
			private Double loanAmount;
			
			public Double getLoanAmount() {
				return loanAmount;
			}
			public void setLoanAmount(Double loanAmount) {
				this.loanAmount = loanAmount;
			}
			public Date getDate() {
				return date;
			}
			public void setDate(Date date) {
				this.date = date;
			}
			public Double getRepaymentAmount() {
				return repaymentAmount;
			}
			public void setRepaymentAmount(Double repaymentAmount) {
				this.repaymentAmount = repaymentAmount;
			}
			public String getMode() {
				return mode;
			}
			public void setMode(String mode) {
				this.mode = mode;
			}
			public String getTenure() {
				return tenure;
			}
			public void setTenure(String tenure) {
				this.tenure = tenure;
			}
			public String getLoanType() {
				return loanType;
			}
			public void setLoanType(String loanType) {
				this.loanType = loanType;
			}
			
		}
		
}
