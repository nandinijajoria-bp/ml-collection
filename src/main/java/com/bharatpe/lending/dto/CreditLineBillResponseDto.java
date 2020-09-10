package com.bharatpe.lending.dto;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

public class CreditLineBillResponseDto {

	private Boolean success=true;
	private String message="";
	private List<Bill> bill;
	
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
	
	public List<Bill> getBill() {
		return bill;
	}

	public void setBill(List<Bill> bill) {
		this.bill = bill;
	}




	@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class Bill{
		
		private Long id;
		private String state;
		private Double billAmount;
		private Double paidAmount;
		private Double minDueAmount;
		private Date billCycleStartDate;
		private Date billCycleEndDate;
		private Date dueDate;
		private Date paidDate;
		private Double payableMad;
		private Double payableAmount;
		private Date generatedDate;
		
		public String getState() {
			return state;
		}
		public void setState(String state) {
			this.state = state;
		}
		public Long getId() {
			return id;
		}
		public void setId(Long id) {
			this.id = id;
		}
		public Double getBillAmount() {
			return billAmount;
		}
		public void setBillAmount(Double billAmount) {
			this.billAmount = billAmount;
		}
		public Double getPaidAmount() {
			return paidAmount;
		}
		public void setPaidAmount(Double paidAmount) {
			this.paidAmount = paidAmount;
		}
		public Double getMinDueAmount() {
			return minDueAmount;
		}
		public void setMinDueAmount(Double minDueAmount) {
			this.minDueAmount = minDueAmount;
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
		public Date getDueDate() {
			return dueDate;
		}
		public void setDueDate(Date dueDate) {
			this.dueDate = dueDate;
		}
		public Date getPaidDate() {
			return paidDate;
		}
		public void setPaidDate(Date paidDate) {
			this.paidDate = paidDate;
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
		public Date getGeneratedDate() {
			return generatedDate;
		}
		public void setGeneratedDate(Date generatedDate) {
			this.generatedDate = generatedDate;
		}
		
	}
	
}
