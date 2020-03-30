package com.bharatpe.lending.dto;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class LiquiloanSettlementRequestDTO {

	private String date;
	private String totalAmount;
	private String utrNumber;
	private String transferDate;
	private List<LoanData> loanDetails;

	@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
	public static class LoanData{
		private String loanId;
		private String urn;
		private String amount;

		public String getLoanId() {
			return loanId;
		}
		public void setLoanId(String loanId) {
			this.loanId = loanId;
		}
		public String getUrn() {
			return urn;
		}
		public void setUrn(String urn) {
			this.urn = urn;
		}
		public String getAmount() {
			return amount;
		}
		public void setAmount(String amount) {
			this.amount = amount;
		}
		@Override
		public String toString() {
			return "LoanData [loanId=" + loanId + ", urn=" + urn + ", amount=" + amount + "]";
		}

	}

	public String getTotalAmount() {
		return totalAmount;
	}

	public void setTotalAmount(String totalAmount) {
		this.totalAmount = totalAmount;
	}

	public String getUtrNumber() {
		return utrNumber;
	}

	public void setUtrNumber(String utrNumber) {
		this.utrNumber = utrNumber;
	}

	public String getTransferDate() {
		return transferDate;
	}

	public void setTransferDate(String transferDate) {
		this.transferDate = transferDate;
	}

	public List<LoanData> getLoanDetails() {
		return loanDetails;
	}

	public void setLoanDetails(List<LoanData> loanDetails) {
		this.loanDetails = loanDetails;
	}

	public String getDate() {
		return date;
	}

	public void setDate(String date) {
		this.date = date;
	}

	@Override
	public String toString() {
		return "LiquiloanSettlementRequestDTO [date=" + date + ", totalAmount=" + totalAmount + ", utrNumber="
				+ utrNumber + ", transferDate=" + transferDate + ", loanDetails=" + loanDetails + "]";
	}


}