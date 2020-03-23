package com.bharatpe.lending.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class LiquiloanCallbackRequestDTO {
	
	private String loanId;
	private String amount;
	private String status;
	private String tenure;
	private String emi;
	private String roi;
	private String urn;
	private String utr;
	private String disbursed_amount;
	private String timestamp;
	private String checksum;

	
	public String getLoanId() {
		return loanId;
	}



	public void setLoanId(String loanId) {
		this.loanId = loanId;
	}



	public String getAmount() {
		return amount;
	}



	public void setAmount(String amount) {
		this.amount = amount;
	}



	public String getStatus() {
		return status;
	}



	public void setStatus(String status) {
		this.status = status;
	}



	public String getTenure() {
		return tenure;
	}



	public void setTenure(String tenure) {
		this.tenure = tenure;
	}



	public String getEmi() {
		return emi;
	}



	public void setEmi(String emi) {
		this.emi = emi;
	}



	public String getRoi() {
		return roi;
	}



	public void setRoi(String roi) {
		this.roi = roi;
	}



	public String getUrn() {
		return urn;
	}



	public void setUrn(String urn) {
		this.urn = urn;
	}



	public String getUtr() {
		return utr;
	}



	public void setUtr(String utr) {
		this.utr = utr;
	}



	public String getDisbursed_amount() {
		return disbursed_amount;
	}



	public void setDisbursed_amount(String disbursed_amount) {
		this.disbursed_amount = disbursed_amount;
	}



	public String getTimestamp() {
		return timestamp;
	}



	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}



	public String getChecksum() {
		return checksum;
	}



	public void setChecksum(String checksum) {
		this.checksum = checksum;
	}



	@Override
	public String toString() {
		return "LiquiloanCallbackRequestDTO [loanId=" + loanId + ", amount=" + amount + ", status=" + status
				+ ", tenure=" + tenure + ", emi=" + emi + ", roi=" + roi + ", urn=" + urn + ", utr=" + utr
				+ ", disbursed_amount=" + disbursed_amount + ", timestamp=" + timestamp + ", checksum=" + checksum
				+ "]";
	}
	
	
	
}
