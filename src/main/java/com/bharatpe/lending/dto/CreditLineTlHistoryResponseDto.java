package com.bharatpe.lending.dto;

import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class CreditLineTlHistoryResponseDto {
	
	private Boolean success=true;
	private String message="";
	private Double ediAmount;
	private Double loanAmount;
	private String spendMode;
	private Integer tenure;
	private Double repaid;
	private Double repaymentAmount;
	private Double interestRate;
	private Date ediStartDate;
	private List<IndividualSettlement> schedule;
	
	@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
	public static class IndividualSettlement{
		
		private Date date;
		private Double ediPaid;
		private Double ediDue;
		private String mode;
		
		public Date getDate() {
			return date;
		}
		public void setDate(Date date) {
			this.date = date;
		}
		public Double getEdiPaid() {
			return ediPaid;
		}
		public void setEdiPaid(Double ediPaid) {
			this.ediPaid = ediPaid;
		}
		public Double getEdiDue() {
			return ediDue;
		}
		public void setEdiDue(Double ediDue) {
			this.ediDue = ediDue;
		}
		public String getMode() {
			return mode;
		}
		public void setMode(String mode) {
			this.mode = mode;
		}
		
	}

	public String getSpendMode() {
		return spendMode;
	}

	public void setSpendMode(String spendMode) {
		this.spendMode = spendMode;
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

	public Double getEdiAmount() {
		return ediAmount;
	}

	public void setEdiAmount(Double ediAmount) {
		this.ediAmount = ediAmount;
	}

	public Double getLoanAmount() {
		return loanAmount;
	}

	public void setLoanAmount(Double loanAmount) {
		this.loanAmount = loanAmount;
	}

	public Integer getTenure() {
		return tenure;
	}

	public void setTenure(Integer tenure) {
		this.tenure = tenure;
	}

	public Double getRepaid() {
		return repaid;
	}

	public void setRepaid(Double repaid) {
		this.repaid = repaid;
	}

	public Double getRepaymentAmount() {
		return repaymentAmount;
	}

	public void setRepaymentAmount(Double repaymentAmount) {
		this.repaymentAmount = repaymentAmount;
	}

	public Double getInterestRate() {
		return interestRate;
	}

	public void setInterestRate(Double interestRate) {
		this.interestRate = interestRate;
	}

	public List<IndividualSettlement> getSchedule() {
		return schedule;
	}

	public void setSchedule(List<IndividualSettlement> schedule) {
		this.schedule = schedule;
	}

	public Date getEdiStartDate() {
		return ediStartDate;
	}

	public void setEdiStartDate(Date ediStartDate) {
		this.ediStartDate = ediStartDate;
	}
	
}
