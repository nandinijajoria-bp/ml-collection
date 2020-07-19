package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SpendModeWidget {
	
	@JsonProperty(value = "BANK_TRANSFER")
	private String bankTransfer="bharatpe://bankTransfer";
	
	@JsonProperty(value = "SEND_MONEY")
	private String sendMoney="bharatpe://sendmoney?resultCode=true";
	
	@JsonProperty(value = "BILL_PAYMENT")
	private String billPayment="bharatpe://dynamic?key=bill-payment-prod&resultCode=true";
	
//	@JsonProperty(value = "SHOPPING")
//	private String shopping="bharatpe://shopping";

	public String getBankTransfer() {
		return bankTransfer;
	}

	public void setBankTransfer(String bankTransfer) {
		this.bankTransfer = bankTransfer;
	}

	public String getSendMoney() {
		return sendMoney;
	}

	public void setSendMoney(String sendMoney) {
		this.sendMoney = sendMoney;
	}

	public String getBillPayment() {
		return billPayment;
	}

	public void setBillPayment(String billPayment) {
		this.billPayment = billPayment;
	}

//	public String getShopping() {
//		return shopping;
//	}
//
//	public void setShopping(String shopping) {
//		this.shopping = shopping;
//	}

//	@Override
//	public String toString() {
//		return "SpendModeWidget [bankTransfer=" + bankTransfer + ", sendMoney=" + sendMoney + ", billPayment="
//				+ billPayment + ", shopping=" + shopping + "]";
//	}

}
