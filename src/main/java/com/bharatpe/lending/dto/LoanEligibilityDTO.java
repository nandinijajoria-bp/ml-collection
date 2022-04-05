package com.bharatpe.lending.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class LoanEligibilityDTO {

	@JsonProperty(value = "finance_charge")
	private Integer processingFee;
	
	@JsonProperty(value = "interest_rate")
	private Double interestRate;
	
	@JsonProperty(value = "interest_amount")
	private Integer interestAmount;
	
	@JsonProperty(value = "disbursement_amount")
	private Integer disbursementAmount;
	
	@JsonProperty(value = "prev_loan_unpaid_amount")
	private Integer prevLoanUnpaidAmount;
	
	@JsonProperty(value = "option_enable")
	private boolean optionEnable;

	@JsonProperty(value = "edi_count")
	private Integer ediCount;
	
	private Integer amount;
	private Integer edi;
	private Integer repayment;
	private String category;
	private String tenure;
	private String construct;
	private String type;
	@JsonIgnore
	private Integer principleEdiTenure;
	private Integer tenureInMonths;

	public Integer getTenureInMonths() {
		return tenureInMonths;
	}

	public void setTenureInMonths(Integer tenureInMonths) {
		this.tenureInMonths = tenureInMonths;
	}

	@JsonProperty(value = "installment_details")
	private List<LabelDTO> list;

	private boolean skipEnatch = true;
	private String enach;
	private String loanType;

	private Integer ioEdi;
	private Integer ioEdiCount;

	public Integer getIoEdi() {
		return ioEdi;
	}

	public void setIoEdi(Integer ioEdi) {
		this.ioEdi = ioEdi;
	}

	public Integer getIoEdiCount() {
		return ioEdiCount;
	}

	public void setIoEdiCount(Integer ioEdiCount) {
		this.ioEdiCount = ioEdiCount;
	}

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
	public Integer getEdi() {
		return edi;
	}
	public void setEdi(Integer edi) {
		this.edi = edi;
	}
	public Integer getPrevLoanUnpaidAmount() {
		return prevLoanUnpaidAmount;
	}
	public void setPrevLoanUnpaidAmount(Integer prevLoanUnpaidAmount) {
		this.prevLoanUnpaidAmount = prevLoanUnpaidAmount;
	}
	public Integer getPrincipleEdiTenure() { return principleEdiTenure; }
	public void setPrincipleEdiTenure(Integer principleEdiTenure) { this.principleEdiTenure = principleEdiTenure; }

	public boolean isSkipEnatch() {
		return skipEnatch;
	}

	public void setSkipEnatch(boolean skipEnatch) {
		this.skipEnatch = skipEnatch;
	}

	public String getEnach() {
		return enach;
	}

	public void setEnach(String enach) {
		this.enach = enach;
	}

	public String getLoanType() {
		return loanType;
	}

	public void setLoanType(String loanType) {
		this.loanType = loanType;
	}

	public Integer getEdiCount() {
		return ediCount;
	}

	public void setEdiCount(Integer ediCount) {
		this.ediCount = ediCount;
	}

	@Override
	public String toString() {
		return "LoanEligibilityDTO [processingFee=" + processingFee + ", interestRate=" + interestRate
				+ ", interestAmount=" + interestAmount + ", disbursementAmount=" + disbursementAmount
				+ ", prevLoanUnpaidAmount=" + prevLoanUnpaidAmount + ", optionEnable=" + optionEnable + ", amount="
				+ amount + ", edi=" + edi + ", repayment=" + repayment + ", category=" + category + ", tenure=" + tenure
				+ ", construct=" + construct + ", type=" + type + ", list=" + list + "]";
	}
}
