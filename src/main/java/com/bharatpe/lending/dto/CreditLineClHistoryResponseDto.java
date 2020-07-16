package com.bharatpe.lending.dto;

import java.util.Date;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class CreditLineClHistoryResponseDto {
	
	private Boolean success=true;
	private String message="";
	private Double amount;
	private String spendMode;
	private String transactionId;
	private String status;
	private Date date;
	private Double availableLimit;
	private Narration detail;
	
	@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
	public static class Narration{
		
		private String narrationHeading;
		private String narration1;
		private String narration2;
		private String narration3;
		private String icon;
		
		public String getNarrationHeading() {
			return narrationHeading;
		}
		public void setNarrationHeading(String narrationHeading) {
			this.narrationHeading = narrationHeading;
		}
		public String getNarration1() {
			return narration1;
		}
		public void setNarration1(String narration1) {
			this.narration1 = narration1;
		}
		public String getNarration2() {
			return narration2;
		}
		public void setNarration2(String narration2) {
			this.narration2 = narration2;
		}
		public String getNarration3() {
			return narration3;
		}
		public void setNarration3(String narration3) {
			this.narration3 = narration3;
		}
		public String getIcon() {
			return icon;
		}
		public void setIcon(String icon) {
			this.icon = icon;
		}
		
	}
	
	public Double getAvailableLimit() {
		return availableLimit;
	}
	public void setAvailableLimit(Double availableLimit) {
		this.availableLimit = availableLimit;
	}
	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	public Narration getDetail() {
		return detail;
	}
	public void setDetail(Narration detail) {
		this.detail = detail;
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
	public Double getAmount() {
		return amount;
	}
	public void setAmount(Double amount) {
		this.amount = amount;
	}
	public String getSpendMode() {
		return spendMode;
	}
	public void setSpendMode(String spendMode) {
		this.spendMode = spendMode;
	}
	public String getTransactionId() {
		return transactionId;
	}
	public void setTransactionId(String transactionId) {
		this.transactionId = transactionId;
	}
	public Date getDate() {
		return date;
	}
	public void setDate(Date date) {
		this.date = date;
	}
	
}
