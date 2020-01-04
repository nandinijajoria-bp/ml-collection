package com.bharatpe.lending.util;

import com.bharatpe.common.entities.AvailableLoan;
import com.bharatpe.common.entities.LendingCategories;

public class LoanCalculationUtil {
	
	public static LoanBreakupDetail getLoanBreakup(AvailableLoan availableLoan, LendingCategories category) {
		
		LoanBreakupDetail breakup = new LoanBreakupDetail();
		
		Integer edi, ioEdi, processingFee, ioInterestAmount, interestAmount, totalInterestAmount, ioOrFreeEdiTenure, principleEdiTenure, repayment, disbursementAmount;
		Double effectiveInterestRate = null;
		
		if("CONSTRUCT_2".equals(availableLoan.getLoanConstruct())) {
			processingFee = (int) Math.ceil(availableLoan.getAmount() * Double.valueOf(category.getProcessingFee()));
			edi = (int) Math.ceil((availableLoan.getAmount() + (availableLoan.getAmount() * (category.getInterestRate() / 100) * (category.getTenureMonths())))) / category.getPayableDays();
			repayment = (int) Math.round(category.getPayableDays() * edi);
			totalInterestAmount = interestAmount = repayment - availableLoan.getAmount().intValue();
			effectiveInterestRate = ((interestAmount / availableLoan.getAmount()) / (category.getTenureMonths())) * 100;
			ioOrFreeEdiTenure = 1;
			ioEdi = ioInterestAmount = 0;
			principleEdiTenure = category.getTenureMonths().intValue() - ioOrFreeEdiTenure;
		} else if("CONSTRUCT_3".equals(availableLoan.getLoanConstruct())) {
			processingFee = (int) Math.ceil(availableLoan.getAmount() * Double.valueOf(category.getProcessingFee()));
			ioEdi = (int) Math.ceil((availableLoan.getAmount() + (availableLoan.getAmount() * (category.getInterestRate() / 100) * category.getIoTenureMonths()))) / category.getIoPayableDays();
			edi = (int) Math.ceil((availableLoan.getAmount() + (availableLoan.getAmount() * (category.getInterestRate() / 100) * (category.getTenureMonths() - 1)))) / category.getPayableDays();
			repayment = (int) Math.round((category.getPayableDays() * edi) + (category.getIoPayableDays() * ioEdi));
			ioInterestAmount = category.getIoPayableDays() * ioEdi;
			interestAmount = repayment - ioInterestAmount - availableLoan.getAmount().intValue();
			totalInterestAmount = ioInterestAmount + interestAmount;
			effectiveInterestRate = ((interestAmount + ioInterestAmount / availableLoan.getAmount()) / (category.getTenureMonths())) * 100;
			ioOrFreeEdiTenure = category.getIoTenureMonths().intValue();
			principleEdiTenure = category.getTenureMonths().intValue() - ioOrFreeEdiTenure;
		} else {
			processingFee = Integer.valueOf(category.getProcessingFee());
			edi = (int) Math.ceil((availableLoan.getAmount() + (availableLoan.getAmount() * (category.getInterestRate() / 100) * category.getTenureMonths()))) / category.getPayableDays();
			repayment = (int) Math.round(category.getPayableDays() * edi);
			totalInterestAmount = interestAmount = repayment - availableLoan.getAmount().intValue();
			effectiveInterestRate = ((interestAmount / availableLoan.getAmount()) / category.getTenureMonths()) * 100;
			ioOrFreeEdiTenure = 0;
			principleEdiTenure = category.getTenureMonths().intValue();
			ioEdi = ioInterestAmount = 0;
		}
		disbursementAmount = availableLoan.getAmount().intValue() - processingFee; 
		
		breakup.setConstruct(availableLoan.getLoanConstruct());
		breakup.setEdi(principleEdiTenure);
		breakup.setIoEdi(ioEdi);
		breakup.setProcessingFee(processingFee);
		breakup.setIoInterestAmount(ioInterestAmount);
		breakup.setInterestAmount(totalInterestAmount);
		breakup.setTotalInterestAmount(totalInterestAmount);
		breakup.setIoOrFreeEdiTenure(ioOrFreeEdiTenure);
		breakup.setPrincipleEdiTenure(principleEdiTenure);
		breakup.setRepayment(repayment);
		breakup.setEffectiveInterestRate(effectiveInterestRate);
		breakup.setDisbursementAmount(disbursementAmount);
		breakup.setLoanType(getType(availableLoan.getLoanConstruct()));
		return breakup;
		
	
	}
	
	public static String getType(String loanConstruct) {
		if("CONSTRUCT_1".equals(loanConstruct)) {
			return null;
		} else if("CONSTRUCT_2".equals(loanConstruct)) {
			return "1st Month Free";
		} else if("CONSTRUCT_3".equals(loanConstruct)) {
			return "Only Interest";
		}
		return null;
	}

	public static class LoanBreakupDetail {
		private String construct;
		private Integer edi;
		private Integer ioEdi;
		private Integer processingFee;
		private Integer ioInterestAmount;
		private Integer interestAmount;
		private Integer totalInterestAmount;
		private Integer ioOrFreeEdiTenure;
		private Integer principleEdiTenure;
		private Integer repayment;
		private Integer disbursementAmount;;
		private Double effectiveInterestRate;
		private String type;
		
		public String getConstruct() {
			return construct;
		}
		public void setConstruct(String construct) {
			this.construct = construct;
		}
		public Integer getEdi() {
			return edi;
		}
		public void setEdi(Integer edi) {
			this.edi = edi;
		}
		public Integer getIoEdi() {
			return ioEdi;
		}
		public void setIoEdi(Integer ioEdi) {
			this.ioEdi = ioEdi;
		}
		public Integer getProcessingFee() {
			return processingFee;
		}
		public void setProcessingFee(Integer processingFee) {
			this.processingFee = processingFee;
		}
		public Integer getIoInterestAmount() {
			return ioInterestAmount;
		}
		public void setIoInterestAmount(Integer ioInterestAmount) {
			this.ioInterestAmount = ioInterestAmount;
		}
		public Integer getInterestAmount() {
			return interestAmount;
		}
		public void setInterestAmount(Integer interestAmount) {
			this.interestAmount = interestAmount;
		}
		public Integer getTotalInterestAmount() {
			return totalInterestAmount;
		}
		public void setTotalInterestAmount(Integer totalInterestAmount) {
			this.totalInterestAmount = totalInterestAmount;
		}
		public Double getEffectiveInterestRate() {
			return effectiveInterestRate;
		}
		public void setEffectiveInterestRate(Double effectiveInterestRate) {
			this.effectiveInterestRate = effectiveInterestRate;
		}
		public Integer getIoOrFreeEdiTenure() {
			return ioOrFreeEdiTenure;
		}
		public void setIoOrFreeEdiTenure(Integer ioOrFreeEdiTenure) {
			this.ioOrFreeEdiTenure = ioOrFreeEdiTenure;
		}
		public Integer getPrincipleEdiTenure() {
			return principleEdiTenure;
		}
		public void setPrincipleEdiTenure(Integer principleEdiTenure) {
			this.principleEdiTenure = principleEdiTenure;
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
		public String getType() {
			return type;
		}
		public void setLoanType(String type) {
			this.type = type;
		}
		@Override
		public String toString() {
			return "LoanBreakupDetail [construct=" + construct + ", edi=" + edi + ", ioEdi=" + ioEdi
					+ ", processingFee=" + processingFee + ", ioInterestAmount=" + ioInterestAmount
					+ ", interestAmount=" + interestAmount + ", totalInterestAmount=" + totalInterestAmount
					+ ", ioOrFreeEdiTenure=" + ioOrFreeEdiTenure + ", principleEdiTenure=" + principleEdiTenure
					+ ", repayment=" + repayment + ", effectiveInterestRate=" + effectiveInterestRate + "]";
		}
		
	}
}
