package com.bharatpe.lending.dto;

import java.util.Date;
import java.util.List;

public class BillDetailResponseDto {
	
	private Boolean success=true;
	private String message="";
	private String merchantName;
	private String mobile;
	private Date billCycleStartDate;
	private Date billCycleEndDate;
	private Double minAmountDue;
	private Date dueDate;
	private Double totalPayable;
	private Double usedLimit;
	private Double interestPayable;
	private Double availableLimit;
	private String pdfUrl;
	private Double payableMad;
	private Double payableAmount;
	private List<Transaction> transactions;
	
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

	public String getMerchantName() {
		return merchantName;
	}

	public void setMerchantName(String merchantName) {
		this.merchantName = merchantName;
	}

	public String getMobile() {
		return mobile;
	}

	public void setMobile(String mobile) {
		this.mobile = mobile;
	}

	public Date getBillCycleStartDate() {
		return billCycleStartDate;
	}

	public void setBillCycleStartDate(Date billCycleStartDate) {
		this.billCycleStartDate = billCycleStartDate;
	}

	public Date getBillCycleEndDate() {
		return billCycleEndDate;
	}

	public void setBillCycleEndDate(Date billCycleEndDate) {
		this.billCycleEndDate = billCycleEndDate;
	}

	public Double getMinAmountDue() {
		return minAmountDue;
	}

	public void setMinAmountDue(Double minAmountDue) {
		this.minAmountDue = minAmountDue;
	}

	public Date getDueDate() {
		return dueDate;
	}

	public void setDueDate(Date dueDate) {
		this.dueDate = dueDate;
	}

	public Double getTotalPayable() {
		return totalPayable;
	}

	public void setTotalPayable(Double totalPayable) {
		this.totalPayable = totalPayable;
	}

	public Double getUsedLimit() {
		return usedLimit;
	}

	public void setUsedLimit(Double usedLimit) {
		this.usedLimit = usedLimit;
	}

	public Double getInterestPayable() {
		return interestPayable;
	}

	public void setInterestPayable(Double interestPayable) {
		this.interestPayable = interestPayable;
	}

	public Double getAvailableLimit() {
		return availableLimit;
	}

	public void setAvailableLimit(Double availableLimit) {
		this.availableLimit = availableLimit;
	}

	public String getPdfUrl() {
		return pdfUrl;
	}

	public void setPdfUrl(String pdfUrl) {
		this.pdfUrl = pdfUrl;
	}

	public List<Transaction> getTransactions() {
		return transactions;
	}

	public void setTransactions(List<Transaction> transactions) {
		this.transactions = transactions;
	}

	public Double getPayableMad() {
		return payableMad;
	}

	public void setPayableMad(Double payableMad) {
		this.payableMad = payableMad;
	}

	public Double getPayableAmount() {
		return payableAmount;
	}

	public void setPayableAmount(Double payableAmount) {
		this.payableAmount = payableAmount;
	}

	public static class Transaction{
		
		private Date date;
		private String type;
		private String subType;
		private Double amount;
		
		public Date getDate() {
			return date;
		}
		public void setDate(Date date) {
			this.date = date;
		}
		public String getType() {
			return type;
		}
		public void setType(String type) {
			this.type = type;
		}
		public String getSubType() {
			return subType;
		}
		public void setSubType(String subType) {
			this.subType = subType;
		}
		public Double getAmount() {
			return amount;
		}
		public void setAmount(Double amount) {
			this.amount = amount;
		}
	
	}
}
