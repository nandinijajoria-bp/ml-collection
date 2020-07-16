package com.bharatpe.lending.dto;

import java.util.Date;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class RepaymentWidget {
	
	private Double payableAmount=0D;
	private Date billDate;
	private Double minDueAmount=0D;
	private Double ediAmount=0D;
	private Date dueDate;
	private Long billId;
	
	public Double getPayableAmount() {
		return payableAmount;
	}
	public void setPayableAmount(Double payableAmount) {
		this.payableAmount = payableAmount;
	}
	public Date getBillDate() {
		return billDate;
	}
	public void setBillDate(Date billDate) {
		this.billDate = billDate;
	}
	public Double getMinDueAmount() {
		return minDueAmount;
	}
	public void setMinDueAmount(Double minDueAmount) {
		this.minDueAmount = minDueAmount;
	}
	public Double getEdiAmount() {
		return ediAmount;
	}
	public void setEdiAmount(Double ediAmount) {
		this.ediAmount = ediAmount;
	}
	public Date getDueDate() {
		return dueDate;
	}
	public void setDueDate(Date dueDate) {
		this.dueDate = dueDate;
	}
	public Long getBillId() {
		return billId;
	}
	public void setBillId(Long billId) {
		this.billId = billId;
	}
	
}
