package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;


public class DashboardDetailsResponseDto {

	 private Boolean success=true;
	 
	 private String message="";
	
	 @JsonProperty(value = "credit_widget")
	 private CreditLimitWidget creditWidget;
	 
	 @JsonProperty(value = "spend_mode_widget")
	 private SpendModeWidget spendModeWidget;
	 
	 @JsonProperty(value = "repayment_widget")
	 private RepaymentWidget repaymentWidget=new RepaymentWidget();
	 
	 @JsonProperty(value="transaction_done")
	 private Boolean transactionDone=true;
	 
	 @JsonProperty(value = "account_blocked")
	 private Boolean accountBlocked=false;
	 
	 @JsonProperty(value = "merchant_name")
     private String merchantName;
     
	 @JsonProperty(value = "merchant_bank_name")
     private String merchantBankName;
     
	 @JsonProperty(value = "merchant_acc_no")
     private String merchantAccNo;
     
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
	public CreditLimitWidget getCreditWidget() {
		return creditWidget;
	}
	public void setCreditWidget(CreditLimitWidget creditWidget) {
		this.creditWidget = creditWidget;
	}
	public SpendModeWidget getSpendModeWidget() {
		return spendModeWidget;
	}
	public void setSpendModeWidget(SpendModeWidget spendModeWidget) {
		this.spendModeWidget = spendModeWidget;
	}
	public RepaymentWidget getRepaymentWidget() {
		return repaymentWidget;
	}
	public void setRepaymentWidget(RepaymentWidget repaymentWidget) {
		this.repaymentWidget = repaymentWidget;
	}
	public Boolean getAccountBlocked() {
		return accountBlocked;
	}
	public void setAccountBlocked(Boolean accountBlocked) {
		this.accountBlocked = accountBlocked;
	}
	public String getMerchantName() {
		return merchantName;
	}
	public void setMerchantName(String merchantName) {
		this.merchantName = merchantName;
	}
	public String getMerchantBankName() {
		return merchantBankName;
	}
	public void setMerchantBankName(String merchantBankName) {
		this.merchantBankName = merchantBankName;
	}
	public String getMerchantAccNo() {
		return merchantAccNo;
	}
	public void setMerchantAccNo(String merchantAccNo) {
		this.merchantAccNo = merchantAccNo;
	}
	public Boolean getTransactionDone() {
		return transactionDone;
	}
	public void setTransactionDone(Boolean transactionDone) {
		this.transactionDone = transactionDone;
	}
	@Override
	public String toString() {
		return "DashboardDetailsResponseDto [success=" + success + ", message=" + message + ", creditWidget="
				+ creditWidget + ", spendModeWidget=" + spendModeWidget + ", repaymentWidget=" + repaymentWidget
				+ ", transactionDone=" + transactionDone + ", accountBlocked=" + accountBlocked + ", merchantName="
				+ merchantName + ", merchantBankName=" + merchantBankName + ", merchantAccNo=" + merchantAccNo + "]";
	}
	
}
