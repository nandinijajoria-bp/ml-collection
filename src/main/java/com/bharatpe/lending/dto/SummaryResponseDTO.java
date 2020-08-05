package com.bharatpe.lending.dto;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public class SummaryResponseDTO {
	
	private Boolean success=true;
	
	private String message;
	
	private Double avaliableAmount;

	public SummaryResponseDTO(Boolean success, String message) {
		super();
		this.success = success;
		this.message = message;
	}

	public SummaryResponseDTO() {
		super();
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

	private List<Summary> summarylist;
	
	
  
  
  public Double getAvaliableAmount() {
		return avaliableAmount;
	}

	public void setAvaliableAmount(Double avaliableAmount) {
		this.avaliableAmount = avaliableAmount;
	}

	 
 
public List<Summary> getSummarylist() {
		return summarylist;
	}

	public void setSummarylist(List<Summary> summarylist) {
		this.summarylist = summarylist;
	}



 




@Override
	public String toString() {
		return "SummaryResponseDTO [success=" + success + ", message=" + message + ", avaliableAmount="
				+ avaliableAmount + ", summarylist=" + summarylist + "]";
	}








public static class Summary
  {
	  private Date date;
	  
	  private List<Transactions>transactionslist;

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public List<Transactions> getTransactionslist() {
		return transactionslist;
	}

	public void setTransactionslist(List<Transactions> transactionslist) {
		this.transactionslist = transactionslist;
	}

	@Override
	public String toString() {
		return "Summary [date=" + date + ", transactionslist=" + transactionslist + "]";
	}

 
	  
	  
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Transactions
  {	
	  private Date date;
	  private String type;
	  private String mode;
	  private Double amount;
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public String getMode() {
		return mode;
	}
	public void setMode(String mode) {
		this.mode = mode;
	}
	public Double getAmount() {
		return amount;
	}
	public void setAmount(Double amount) {
		this.amount = amount;
	}
	public Date getDate() {
		return date;
	}
	public void setDate(Date date) {
		this.date = date;
	}
	@Override
	public String toString() {
		return "Transactions [date=" + date + ", type=" + type + ", mode=" + mode + ", amount=" + amount + "]";
	} 
	  
  }

}
