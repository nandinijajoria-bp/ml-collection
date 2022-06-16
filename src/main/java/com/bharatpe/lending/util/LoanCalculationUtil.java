package com.bharatpe.lending.util;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import com.bharatpe.common.entities.AvailableLoan;
import com.bharatpe.common.entities.EligibleLoan;
import com.bharatpe.common.entities.LendingCategories;
import com.bharatpe.common.utils.CurrencyUtils;
import com.bharatpe.lending.common.slave.entity.AvailableLoanSlave;
import com.bharatpe.lending.dto.LabelDTO;

public class LoanCalculationUtil {
	
	private static DecimalFormat df = new DecimalFormat("0.00");
	
	public static LoanBreakupDetail getLoanBreakup(AvailableLoan availableLoan, LendingCategories category, String loanType) {
		
		LoanBreakupDetail breakup = new LoanBreakupDetail();
		double interest = "TOPUP".equalsIgnoreCase(loanType) ? 1.75 : category.getInterestRate();
		
		Integer edi, ioEdi, processingFee, ioInterestAmount, interestAmount, totalInterestAmount, ioOrFreeEdiTenure, principleEdiTenure, repayment, disbursementAmount;
		Double effectiveInterestRate = null;
		
		if("CONSTRUCT_2".equals(category.getLoanConstruct())) {
			processingFee = getProcessingFee(availableLoan.getAmount(), category);
			edi = (int) Math.ceil(((availableLoan.getAmount() + (availableLoan.getAmount() * (interest / 100) * category.getTenureMonths()))) / category.getPayableDays());
			repayment = (int) Math.round(category.getPayableDays() * edi);
			totalInterestAmount = interestAmount = repayment - availableLoan.getAmount().intValue();
			ioOrFreeEdiTenure = 1;
			ioEdi = ioInterestAmount = 0;
			principleEdiTenure = category.getTenureMonths().intValue() - ioOrFreeEdiTenure;
		} else if("CONSTRUCT_3".equals(category.getLoanConstruct())) {
			ioOrFreeEdiTenure = category.getIoTenureMonths().intValue();
			processingFee = getProcessingFee(availableLoan.getAmount(), category);
			ioEdi = (int) Math.ceil(availableLoan.getAmount() * (interest / 100) * category.getIoTenureMonths() / category.getIoPayableDays());
			edi = (int) Math.ceil(((availableLoan.getAmount() + (availableLoan.getAmount() * (interest / 100) * (category.getTenureMonths() - ioOrFreeEdiTenure)))) / category.getPayableDays());
			repayment = (int) Math.round((category.getPayableDays() * edi) + (category.getIoPayableDays() * ioEdi));
			ioInterestAmount = category.getIoPayableDays() * ioEdi;
			interestAmount = repayment - ioInterestAmount - availableLoan.getAmount().intValue();
			totalInterestAmount = ioInterestAmount + interestAmount;
			principleEdiTenure = category.getTenureMonths().intValue() - ioOrFreeEdiTenure;
		} else {
			processingFee = getProcessingFee(availableLoan.getAmount(), category);
			edi = (int) Math.ceil(((availableLoan.getAmount() + (availableLoan.getAmount() * (interest / 100) * category.getTenureMonths()))) / category.getPayableDays());
			repayment = (int) Math.round(category.getPayableDays() * edi);
			totalInterestAmount = interestAmount = repayment - availableLoan.getAmount().intValue();
			ioOrFreeEdiTenure = 0;
			principleEdiTenure = category.getTenureMonths().intValue();
			ioEdi = ioInterestAmount = 0;
		}
		
		effectiveInterestRate = ((repayment - availableLoan.getAmount())) / (availableLoan.getAmount() * category.getTenureMonths()) * 100;
		disbursementAmount = availableLoan.getAmount().intValue() - processingFee; 
		
		breakup.setConstruct(category.getLoanConstruct());
		breakup.setEdi(edi);
		breakup.setIoEdi(ioEdi);
		breakup.setProcessingFee(processingFee);
		breakup.setIoInterestAmount(ioInterestAmount);
		breakup.setInterestAmount(totalInterestAmount);
		breakup.setTotalInterestAmount(totalInterestAmount);
		breakup.setIoOrFreeEdiTenure(ioOrFreeEdiTenure);
		breakup.setPrincipleEdiTenure(principleEdiTenure);
		breakup.setRepayment(repayment);
		breakup.setEffectiveInterestRate(Double.valueOf(df.format(effectiveInterestRate)));
		breakup.setDisbursementAmount(disbursementAmount);
		breakup.setType(getType(category.getLoanConstruct()));
		breakup.setLoanAmount(availableLoan.getAmount().intValue());
		breakup.setEdiDays(category.getPayableDays());
		breakup.setIoEdiDays(category.getIoPayableDays());
		breakup.setCategory(category.getCategory());
		breakup.setInterestRate(category.getInterestRate());
		return breakup;
		
	
	}

	public static LoanBreakupDetail getLoanBreakup(AvailableLoanSlave availableLoan, LendingCategories category, String loanType) {

		LoanBreakupDetail breakup = new LoanBreakupDetail();
		double interest = "TOPUP".equalsIgnoreCase(loanType) ? 1.75 : category.getInterestRate();

		Integer edi, ioEdi, processingFee, ioInterestAmount, interestAmount, totalInterestAmount, ioOrFreeEdiTenure, principleEdiTenure, repayment, disbursementAmount;
		Double effectiveInterestRate = null;

		if("CONSTRUCT_2".equals(category.getLoanConstruct())) {
			processingFee = getProcessingFee(availableLoan.getAmount(), category);
			edi = (int) Math.ceil(((availableLoan.getAmount() + (availableLoan.getAmount() * (interest / 100) * category.getTenureMonths()))) / category.getPayableDays());
			repayment = (int) Math.round(category.getPayableDays() * edi);
			totalInterestAmount = interestAmount = repayment - availableLoan.getAmount().intValue();
			ioOrFreeEdiTenure = 1;
			ioEdi = ioInterestAmount = 0;
			principleEdiTenure = category.getTenureMonths().intValue() - ioOrFreeEdiTenure;
		} else if("CONSTRUCT_3".equals(category.getLoanConstruct())) {
			ioOrFreeEdiTenure = category.getIoTenureMonths().intValue();
			processingFee = getProcessingFee(availableLoan.getAmount(), category);
			ioEdi = (int) Math.ceil(availableLoan.getAmount() * (interest / 100) * category.getIoTenureMonths() / category.getIoPayableDays());
			edi = (int) Math.ceil(((availableLoan.getAmount() + (availableLoan.getAmount() * (interest / 100) * (category.getTenureMonths() - ioOrFreeEdiTenure)))) / category.getPayableDays());
			repayment = (int) Math.round((category.getPayableDays() * edi) + (category.getIoPayableDays() * ioEdi));
			ioInterestAmount = category.getIoPayableDays() * ioEdi;
			interestAmount = repayment - ioInterestAmount - availableLoan.getAmount().intValue();
			totalInterestAmount = ioInterestAmount + interestAmount;
			principleEdiTenure = category.getTenureMonths().intValue() - ioOrFreeEdiTenure;
		} else {
			processingFee = getProcessingFee(availableLoan.getAmount(), category);
			edi = (int) Math.ceil(((availableLoan.getAmount() + (availableLoan.getAmount() * (interest / 100) * category.getTenureMonths()))) / category.getPayableDays());
			repayment = (int) Math.round(category.getPayableDays() * edi);
			totalInterestAmount = interestAmount = repayment - availableLoan.getAmount().intValue();
			ioOrFreeEdiTenure = 0;
			principleEdiTenure = category.getTenureMonths().intValue();
			ioEdi = ioInterestAmount = 0;
		}

		effectiveInterestRate = ((repayment - availableLoan.getAmount())) / (availableLoan.getAmount() * category.getTenureMonths()) * 100;
		disbursementAmount = availableLoan.getAmount().intValue() - processingFee;

		breakup.setConstruct(category.getLoanConstruct());
		breakup.setEdi(edi);
		breakup.setIoEdi(ioEdi);
		breakup.setProcessingFee(processingFee);
		breakup.setIoInterestAmount(ioInterestAmount);
		breakup.setInterestAmount(totalInterestAmount);
		breakup.setTotalInterestAmount(totalInterestAmount);
		breakup.setIoOrFreeEdiTenure(ioOrFreeEdiTenure);
		breakup.setPrincipleEdiTenure(principleEdiTenure);
		breakup.setRepayment(repayment);
		breakup.setEffectiveInterestRate(Double.valueOf(df.format(effectiveInterestRate)));
		breakup.setDisbursementAmount(disbursementAmount);
		breakup.setType(getType(category.getLoanConstruct()));
		breakup.setLoanAmount(availableLoan.getAmount().intValue());
		breakup.setEdiDays(category.getPayableDays());
		breakup.setIoEdiDays(category.getIoPayableDays());
		breakup.setCategory(category.getCategory());
		breakup.setInterestRate(category.getInterestRate());
		return breakup;


	}


	public static int getProcessingFee(Double loanAmount, LendingCategories category) {
		if(category!=null && category.getProcessingFeeType()!=null) {
			return category.getProcessingFeeType().equalsIgnoreCase("PERCENTAGE")?(int)Math.ceil(loanAmount * Double.parseDouble(category.getProcessingFee())):Integer.parseInt(category.getProcessingFee());
		}
		return 0;
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
		private String type;
		private Integer loanAmount;
		private Double effectiveInterestRate;
		private Integer ediDays;
		private Integer ioEdiDays = 0;
		private String category;
		private Double interestRate;

		public LoanBreakupDetail(String construct, Integer edi, Integer ioEdi, Integer processingFee, Integer ioInterestAmount, Integer interestAmount, Integer totalInterestAmount, Integer ioOrFreeEdiTenure, Integer principleEdiTenure, Integer repayment, Integer disbursementAmount, String type, Integer loanAmount, Double effectiveInterestRate) {
			this.construct = construct;
			this.edi = edi;
			this.ioEdi = ioEdi;
			this.processingFee = processingFee;
			this.ioInterestAmount = ioInterestAmount;
			this.interestAmount = interestAmount;
			this.totalInterestAmount = totalInterestAmount;
			this.ioOrFreeEdiTenure = ioOrFreeEdiTenure;
			this.principleEdiTenure = principleEdiTenure;
			this.repayment = repayment;
			this.disbursementAmount = disbursementAmount;
			this.type = type;
			this.loanAmount = loanAmount;
			this.effectiveInterestRate = effectiveInterestRate;
		}

		public LoanBreakupDetail() {
		}

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
		public Integer getLoanAmount() {
			return loanAmount;
		}
		public void setLoanAmount(Integer loanAmount) {
			this.loanAmount = loanAmount;
		}
		public void setType(String type) {
			this.type = type;
		}

		public Integer getEdiDays() {
			return ediDays;
		}

		public void setEdiDays(Integer ediDays) {
			this.ediDays = ediDays;
		}

		public Integer getIoEdiDays() {
			return ioEdiDays;
		}

		public void setIoEdiDays(Integer ioEdiDays) {
			this.ioEdiDays = ioEdiDays;
		}

		public String getCategory() {
			return category;
		}

		public void setCategory(String category) {
			this.category = category;
		}

		public Double getInterestRate() {
			return interestRate;
		}

		public void setInterestRate(Double interestRate) {
			this.interestRate = interestRate;
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

	public static List<LabelDTO> prepareLabels(LoanBreakupDetail breakup, int months) {
		List<LabelDTO> list = new ArrayList<>();

//		if("CONSTRUCT_1".equals(breakup.getConstruct())) {
			list.add(new LabelDTO("Daily Installment", "₹" + CurrencyUtils.formatInt(breakup.getEdi()) + "/day"));
			list.add(new LabelDTO("No Installment on", "Sundays"));
			list.add(new LabelDTO("Repayment Amount", "₹" + CurrencyUtils.formatInt(breakup.getRepayment())));
//		} else if("CONSTRUCT_2".equals(breakup.getConstruct())) {
//			list.add(new LabelDTO("EDI for 1st Month", "ZERO"));
//			list.add(new LabelDTO("EDI for Next " + breakup.getPrincipleEdiTenure() + " Months", "₹" + CurrencyUtils.formatInt(breakup.getEdi()) + "/day"));
//			list.add(new LabelDTO("No EDI on", "Sundays"));
//			list.add(new LabelDTO("Repayment Amount", "₹" +String.valueOf(breakup.getRepayment())));
//		} else if("CONSTRUCT_3".equals(breakup.getConstruct())) {
//			if (months > 1) {
//				list.add(new LabelDTO("EDI for 1st "+months+" Months", "₹" + CurrencyUtils.formatInt(breakup.getIoEdi()) + "/day"));
//			} else {
//				list.add(new LabelDTO("EDI for 1st Month", "₹" + CurrencyUtils.formatInt(breakup.getIoEdi()) + "/day"));
//			}
//			list.add(new LabelDTO("EDI for Next " + breakup.getPrincipleEdiTenure() + " Months", "₹" + CurrencyUtils.formatInt(breakup.getEdi()) + "/day"));
//			list.add(new LabelDTO("No EDI on", "Sundays"));
//			list.add(new LabelDTO("Repayment Amount", "₹" + CurrencyUtils.formatInt(breakup.getRepayment())));
//		} else {
//			throw new RuntimeException("Construct not defined.");
//		}
		return list;
	}
}
