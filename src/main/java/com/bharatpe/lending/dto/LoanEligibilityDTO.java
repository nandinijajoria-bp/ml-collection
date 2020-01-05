package com.bharatpe.lending.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LoanEligibilityDTO {

	@JsonProperty(value = "processing_fee")
	private Integer processingFee;
	
	@JsonProperty(value = "interest_rate")
	private Double interestRate;
	
	@JsonProperty(value = "interest_amount")
	private Integer interestAmount;
	
	@JsonProperty(value = "disbursement_amount")
	private Integer disbursementAmount;
	
	@JsonProperty(value = "option_enable")
	private boolean optionEnable;

	private Integer amount;
	private Integer repayment;
	private String category;
	private String tenure;
	private String construct;
	private List<LabelDTO> list;
	private String type;
	
	public Integer getProcessingFee() {
		return processingFee;
	}
	public void setProcessingFee(Integer processingFee) {
		this.processingFee = processingFee;
	}
	public Double getInterestRate() {
		return interestRate;
	}
	public void setInterestRate(Double interestRate) {
		this.interestRate = interestRate;
	}
	public Integer getInterestAmount() {
		return interestAmount;
	}
	public void setInterestAmount(Integer interestAmount) {
		this.interestAmount = interestAmount;
	}
	public Integer getDisbursementAmount() {
		return disbursementAmount;
	}
	public void setDisbursementAmount(Integer disbursementAmount) {
		this.disbursementAmount = disbursementAmount;
	}
	public boolean isOptionEnable() {
		return optionEnable;
	}
	public void setOptionEnable(boolean optionEnable) {
		this.optionEnable = optionEnable;
	}
	public Integer getAmount() {
		return amount;
	}
	public void setAmount(Integer amount) {
		this.amount = amount;
	}
	public String getCategory() {
		return category;
	}
	public void setCategory(String category) {
		this.category = category;
	}
	public String getTenure() {
		return tenure;
	}
	public void setTenure(String tenure) {
		this.tenure = tenure;
	}
	public String getConstruct() {
		return construct;
	}
	public void setConstruct(String construct) {
		this.construct = construct;
	}
	public List<LabelDTO> getList() {
		return list;
	}
	public void setList(List<LabelDTO> list) {
		this.list = list;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public Integer getRepayment() {
		return repayment;
	}
	public void setRepayment(Integer repayment) {
		this.repayment = repayment;
	}
	
	@Override
	public String toString() {
		return "LoanEligibilityDTO [processingFee=" + processingFee + ", interestRate=" + interestRate
				+ ", interestAmount=" + interestAmount + ", disbursementAmount=" + disbursementAmount
				+ ", optionEnable=" + optionEnable + ", amount=" + amount + ", category=" + category + ", tenure="
				+ tenure + ", construct=" + construct + ", list=" + list + ", type=" + type + "]";
	}
}
