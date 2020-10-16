package com.bharatpe.lending.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LoanHistoryDTO {

	private Long id;
	private Double amount;

	@JsonProperty(value = "start_date")
	private Date startDate;

	@JsonProperty(value = "end_date")
	private Date endDate;

	@JsonProperty(value = "status")
	private String status;

	@JsonProperty(value = "loan_status_title")
	private String loanStatusTitle;

	@JsonProperty(value = "loan_status_message")
	private String loanStatusMessage;

	@JsonProperty(value = "loan_status_header")
	private String loanStatusHeader;

	private Double repaid;
	private Double due;
	private Double edi;
	private boolean showPaynow;

	private Double disbursalAmount;
	private Double processingFee;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Double getAmount() {
		return amount;
	}

	public void setAmount(Double amount) {
		this.amount = amount;
	}

	public Date getStartDate() {
		return startDate;
	}

	public void setStartDate(Date startDate) {
		this.startDate = startDate;
	}

	public Date getEndDate() {
		return endDate;
	}

	public void setEndDate(Date endDate) {
		this.endDate = endDate;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getLoanStatusTitle() {
		return loanStatusTitle;
	}

	public void setLoanStatusTitle(String loanStatusTitle) {
		this.loanStatusTitle = loanStatusTitle;
	}

	public String getLoanStatusMessage() {
		return loanStatusMessage;
	}

	public void setLoanStatusMessage(String loanStatusMessage) {
		this.loanStatusMessage = loanStatusMessage;
	}

	public Double getRepaid() {
		return repaid;
	}

	public void setRepaid(Double repaid) {
		this.repaid = repaid;
	}

	public Double getDue() {
		return due;
	}

	public void setDue(Double due) {
		this.due = due;
	}

	public String getLoanStatusHeader() {
		return loanStatusHeader;
	}

	public void setLoanStatusHeader(String loanStatusHeader) {
		this.loanStatusHeader = loanStatusHeader;
	}
	
	public Double getEdi() {
		return edi;
	}

	public void setEdi(Double edi) {
		this.edi = edi;
	}

	public boolean isShowPaynow() {
		return showPaynow;
	}

	public void setShowPaynow(boolean showPaynow) {
		this.showPaynow = showPaynow;
	}

	public Double getDisbursalAmount() {
		return disbursalAmount;
	}

	public void setDisbursalAmount(Double disbursalAmount) {
		this.disbursalAmount = disbursalAmount;
	}

	public Double getProcessingFee() {
		return processingFee;
	}

	public void setProcessingFee(Double processingFee) {
		this.processingFee = processingFee;
	}

	@Override
	public String toString() {
		return "LoanHistoryDTO [id=" + id + ", amount=" + amount + ", startDate=" + startDate + ", endDate=" + endDate
				+ ", status=" + status + ", loanStatusTitle=" + loanStatusTitle + ", loanStatusMessage="
				+ loanStatusMessage + ", repaid=" + repaid + ", due=" + due + "]";
	}

}
