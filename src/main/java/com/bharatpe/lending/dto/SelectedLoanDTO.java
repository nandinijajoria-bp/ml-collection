package com.bharatpe.lending.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SelectedLoanDTO {

	@JsonProperty("id")
	private Long id;

	@JsonProperty("amount")
	private Integer amount;

	@JsonProperty("category")
	private String category;

	@JsonProperty("construct")
	private String construct;

	@JsonProperty("tenure")
	private String tenure;

	@JsonProperty("finance_charge")
	private Integer fincanceCharge;

	@JsonProperty("edi")
	private Double edi;

	@JsonProperty("edi_duration")
	private Long ediDuration;

	@JsonProperty("interest_rate")
	private Double interestRate;

	@JsonProperty("repayment")
	private Integer repayment;

	@JsonProperty("disbursement_amount")
	private Integer disbursementAmount;

	@JsonProperty("interest_amount")
	private Integer interestAmount;

	@JsonProperty("installment_details")
	private List<LabelDTO> installmentDetails;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
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

	public String getConstruct() {
		return construct;
	}

	public void setConstruct(String construct) {
		this.construct = construct;
	}

	public String getTenure() {
		return tenure;
	}

	public void setTenure(String tenure) {
		this.tenure = tenure;
	}

	public Integer getFincanceCharge() {
		return fincanceCharge;
	}

	public void setFincanceCharge(Integer fincanceCharge) {
		this.fincanceCharge = fincanceCharge;
	}

	public Double getEdi() {
		return edi;
	}

	public void setEdi(Double edi) {
		this.edi = edi;
	}

	public Long getEdiDuration() {
		return ediDuration;
	}

	public void setEdiDuration(Long ediDuration) {
		this.ediDuration = ediDuration;
	}

	public Double getInterestRate() {
		return interestRate;
	}

	public void setInterestRate(Double interestRate) {
		this.interestRate = interestRate;
	}

	public Integer getRepayment() {
		return repayment;
	}

	public void setRepayment(Integer repayment) {
		this.repayment = repayment;
	}

	public Integer getDisbursementAmount() {
		return disbursementAmount;
	}

	public void setDisbursementAmount(Integer disbursementAmount) {
		this.disbursementAmount = disbursementAmount;
	}

	public Integer getInterestAmount() {
		return interestAmount;
	}

	public void setInterestAmount(Integer interestAmount) {
		this.interestAmount = interestAmount;
	}

	public List<LabelDTO> getInstallmentDetails() {
		return installmentDetails;
	}

	public void setInstallmentDetails(List<LabelDTO> installmentDetails) {
		this.installmentDetails = installmentDetails;
	}

	@Override
	public String toString() {
		return "SelectedLoanDTO [id=" + id + ", amount=" + amount + ", category=" + category + ", construct="
				+ construct + ", tenure=" + tenure + ", fincanceCharge=" + fincanceCharge + ", edi=" + edi
				+ ", ediDuration=" + ediDuration + ", interestRate=" + interestRate + ", repayment=" + repayment
				+ ", disbursementAmount=" + disbursementAmount + ", interestAmount=" + interestAmount
				+ ", installmentDetails=" + installmentDetails + "]";
	}
	
}
