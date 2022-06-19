package com.bharatpe.lending.util;

import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

//import com.bharatpe.common.entities.LendingPaymentSchedule;
//import com.bharatpe.lending.common.dao.CreditAccountBillDao;
//import com.bharatpe.lending.common.dao.CreditAccountDao;
//import com.bharatpe.lending.common.dao.LendingCaBalanceDetailDao;
//import com.bharatpe.lending.common.entity.*;
//import com.bharatpe.lending.constant.CreditConstants;
//import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

//import com.bharatpe.lending.common.dao.CreditApplicationAddressDao;

@Component
public  class CreditUtil {

//	@Autowired
//	CreditApplicationAddressDao creditApplicationAddressDao;
//
//	@Autowired
//	LendingCaBalanceDetailDao lendingCaBalanceDetailDao;
//
//	@Autowired
//	LendingPaymentScheduleDao lendingPaymentScheduleDao;
//
//	@Autowired
//	CreditAccountBillDao creditAccountBillDao;
//
//	public  Map<String, Object> prepareSelectedLoanForClient(CreditApplication application) {
//		Map<String, Object> selectedLoan = new LinkedHashMap<>();
//
//		selectedLoan.put("Amount",application.getAmount());
//
//		return selectedLoan;
//	}
//
//
//
//	public  Map<String, Object> prepareShopDetailsForClient(CreditApplication application) {
//		Map<String, Object> shopDetails = new LinkedHashMap<>();
//		CreditApplicationAddress creditApplicationAddress=creditApplicationAddressDao.findTop1ByMerchantIdOrderByIdDesc(application.getMerchantId());
//		shopDetails.put("business_name", application.getBusinessName());
//		shopDetails.put("shop_number", creditApplicationAddress.getShopNumber());
//		shopDetails.put("street_address", creditApplicationAddress.getStreetAddress());
//		shopDetails.put("area",creditApplicationAddress.getArea());
//		shopDetails.put("landmark", creditApplicationAddress.getLandmark());
//		shopDetails.put("pincode", creditApplicationAddress.getPincode());
//		shopDetails.put("city", creditApplicationAddress.getCity());
//		shopDetails.put("state", creditApplicationAddress.getState());
//		shopDetails.put("alternative_contact", application.getAlternateMobile());
//		return shopDetails;
//	}
//
//	public static boolean isSufficientBalance(CreditAccount creditAccount, LendingCaBalanceDetail lendingCaBalanceDetail, Integer amount) {
//		if (creditAccount == null || lendingCaBalanceDetail == null) {
//			return false;
//		}
//		return creditAccount.getAvailableBalance() >= amount && lendingCaBalanceDetail.getAvailableBalance() >= amount;
//	}
//
//	public static boolean isSufficientTLBalance(CreditAccount creditAccount, LendingCaBalanceDetail lendingCaBalanceDetail, Integer amount, List<LendingTlDetails> todayLoans) {
//		if (creditAccount == null || lendingCaBalanceDetail == null || amount < 1000) {
//			return false;
//		}
////		if (todayLoans != null && !todayLoans.isEmpty()) {
////			return false;
////		}
//		return creditAccount.getAvailableBalance() >= amount && lendingCaBalanceDetail.getAvailableBalance() >= amount;
//	}
//
//	public static boolean isSufficientCLBalance(LendingCaBalanceDetail lendingCaBalanceDetail, Integer amount, String spendMode, CreditLineCategories creditLineCategories) {
//		if (lendingCaBalanceDetail == null || creditLineCategories == null) {
//			return false;
//		}
//		if (lendingCaBalanceDetail.getAvailableBalance() < amount) {
//			return false;
//		}
//		if (!CreditConstants.SpendGroup.containsKey(spendMode)) {
//			return false;
//		}
//		//basic cl balance check
//		if (amount > getAvailableCl(lendingCaBalanceDetail, creditLineCategories)) {
//			return false;
//		}
//		//spend mode cl balance check
//		String group = CreditConstants.SpendGroup.get(spendMode);
//		switch (group) {
//			case "G1": {
//				Double limit = lendingCaBalanceDetail.getAccountLimit() * creditLineCategories.getMaxCreditLimit() * creditLineCategories.getG1Limit();
//				double available = limit - lendingCaBalanceDetail.getUsedBalanceG1();
//				return amount <= available;
//			}
//			case "G2": {
//				Double limit = lendingCaBalanceDetail.getAccountLimit() * creditLineCategories.getMaxCreditLimit() * creditLineCategories.getG2Limit();
//				double available = limit - lendingCaBalanceDetail.getUsedBalanceG2();
//				return amount <= available;
//			}
//			case "G3": {
//				Double limit = lendingCaBalanceDetail.getAccountLimit() * creditLineCategories.getMaxCreditLimit() * creditLineCategories.getG3Limit();
//				double available = limit - lendingCaBalanceDetail.getUsedBalanceG3();
//				return amount <= available;
//			}
//		}
//		return false;
//	}
//
//	public static Double getAvailableCl(LendingCaBalanceDetail lendingCaBalanceDetail, CreditLineCategories creditLineCategories) {
//		if (lendingCaBalanceDetail == null || creditLineCategories == null) {
//			return 0D;
//		}
//		Double clLimit = lendingCaBalanceDetail.getAccountLimit() * creditLineCategories.getMaxCreditLimit();
//		return clLimit - lendingCaBalanceDetail.getUsedBalanceCl();
//	}
//
//	public static Double getAvailableClForSpecificMode(LendingCaBalanceDetail lendingCaBalanceDetail, CreditLineCategories creditLineCategories, String spendMode) {
//		if (lendingCaBalanceDetail == null || creditLineCategories == null) {
//			return 0D;
//		}
//		final DecimalFormat df = new DecimalFormat("#.##");
//		String group = CreditConstants.SpendGroup.get(spendMode);
//		switch (group) {
//			case "G1": {
//				Double limit = lendingCaBalanceDetail.getAccountLimit() * creditLineCategories.getMaxCreditLimit() * creditLineCategories.getG1Limit();
//				double available = limit - lendingCaBalanceDetail.getUsedBalanceG1();
//				return Double.valueOf(df.format(available));
//			}
//			case "G2": {
//				Double limit = lendingCaBalanceDetail.getAccountLimit() * creditLineCategories.getMaxCreditLimit() * creditLineCategories.getG2Limit();
//				double available = limit - lendingCaBalanceDetail.getUsedBalanceG2();
//				return Double.valueOf(df.format(available));
//			}
//			case "G3": {
//				Double limit = lendingCaBalanceDetail.getAccountLimit() * creditLineCategories.getMaxCreditLimit() * creditLineCategories.getG3Limit();
//				double available = limit - lendingCaBalanceDetail.getUsedBalanceG3();
//				return Double.valueOf(df.format(available));
//			}
//		}
//		return 0D;
//	}
//
//	public double getPayableAmount(CreditAccount creditAccount) {
//		LendingCaBalanceDetail lendingCaBalanceDetail = lendingCaBalanceDetailDao.findByMerchantIdAndCreditAccountId(creditAccount.getMerchantId(), creditAccount.getId());
//		if (lendingCaBalanceDetail == null) {
//			return 0;
//		}
//		double payableAmount = lendingCaBalanceDetail.getUsedBalanceCl() + lendingCaBalanceDetail.getInterestDue();
//		CreditAccountBill unpaidBill = creditAccountBillDao.getLastUnpaidBill(creditAccount.getId(), creditAccount.getMerchantId());
//		if (unpaidBill != null) {
//			payableAmount += (unpaidBill.getInterestAmount() - unpaidBill.getPaidInterest());
//			payableAmount += (unpaidBill.getPenalty() - unpaidBill.getPaidPenalty());
//		}
//		List<LendingPaymentSchedule> lendingPaymentSchedules = lendingPaymentScheduleDao.findByMerchantIdAndStatusAndCreditLoan(creditAccount.getMerchantId(), "ACTIVE", true);
//		if (!lendingPaymentSchedules.isEmpty()) {
//			for (LendingPaymentSchedule lendingPaymentSchedule : lendingPaymentSchedules) {
//				if (lendingPaymentSchedule.getLoanAmount() != null && lendingPaymentSchedule.getPaidPrinciple() != null) {
//					payableAmount += lendingPaymentSchedule.getLoanAmount() - lendingPaymentSchedule.getPaidPrinciple() + (lendingPaymentSchedule.getDueInterest() != null ? lendingPaymentSchedule.getDueInterest() : 0D);
//				}
//			}
//		}
//		return payableAmount;
//	}
//
//	public double getPayablePrinciple(CreditAccount creditAccount) {
//		LendingCaBalanceDetail lendingCaBalanceDetail = lendingCaBalanceDetailDao.findByMerchantIdAndCreditAccountId(creditAccount.getMerchantId(), creditAccount.getId());
//		if (lendingCaBalanceDetail == null) {
//			return 0;
//		}
//		double payableAmount = lendingCaBalanceDetail.getUsedBalanceCl();
//		List<LendingPaymentSchedule> lendingPaymentSchedules = lendingPaymentScheduleDao.findByMerchantIdAndStatusAndCreditLoan(creditAccount.getMerchantId(), "ACTIVE", true);
//		if (!lendingPaymentSchedules.isEmpty()) {
//			for (LendingPaymentSchedule lendingPaymentSchedule : lendingPaymentSchedules) {
//				if (lendingPaymentSchedule.getLoanAmount() != null && lendingPaymentSchedule.getPaidPrinciple() != null) {
//					payableAmount += lendingPaymentSchedule.getLoanAmount() - lendingPaymentSchedule.getPaidPrinciple();
//				}
//			}
//		}
//		return payableAmount;
//	}
//
//	public double getPayableInterest(CreditAccount creditAccount) {
//		double payableAmount = creditAccount.getInterestDue();
//		CreditAccountBill unpaidBill = creditAccountBillDao.getLastUnpaidBill(creditAccount.getId(), creditAccount.getMerchantId());
//		if (unpaidBill != null) {
//			payableAmount += (unpaidBill.getInterestAmount() - unpaidBill.getPaidInterest());
//			payableAmount += (unpaidBill.getPenalty() - unpaidBill.getPaidPenalty());
//		}
//		List<LendingPaymentSchedule> lendingPaymentSchedules = lendingPaymentScheduleDao.findByMerchantIdAndStatusAndCreditLoan(creditAccount.getMerchantId(), "ACTIVE", true);
//		if (!lendingPaymentSchedules.isEmpty()) {
//			for (LendingPaymentSchedule lendingPaymentSchedule : lendingPaymentSchedules) {
//				if (lendingPaymentSchedule.getDueInterest() != null && lendingPaymentSchedule.getDueInterest() > 0) {
//					payableAmount += lendingPaymentSchedule.getDueInterest();
//				}
//			}
//		}
//		return payableAmount;
//	}
}
