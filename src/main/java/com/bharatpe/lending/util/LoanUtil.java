package com.bharatpe.lending.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.dto.LabelDTO;
import com.bharatpe.lending.dto.SelectedLoanDTO;
import com.bharatpe.lending.dto.ShopDetailsDTO;

public class LoanUtil {
	private static final Logger logger = LoggerFactory.getLogger(LoanUtil.class);

	public static Map<String, Object> prepareSelectedLoanForClient(LendingApplication application) {
		Map<String, Object> selectedLoan = new LinkedHashMap<>();
		
		selectedLoan.put("amount", application.getLoanAmount());
		selectedLoan.put("category", application.getCategory());
		selectedLoan.put("construct", application.getLoanConstruct());
		selectedLoan.put("tenure", application.getTenure());
		selectedLoan.put("id", application.getId());
		selectedLoan.put("finance_charge", application.getProcessingFee());
		selectedLoan.put("edi", application.getEdi());
		selectedLoan.put("edi_duration", application.getPayableDays());
		selectedLoan.put("interest_rate", application.getInterestRate());
		selectedLoan.put("repayment", application.getRepayment());
		selectedLoan.put("disbursement_amount", application.getLoanAmount() - application.getProcessingFee());
		selectedLoan.put("interest_amount", application.getRepayment() - application.getLoanAmount());
		selectedLoan.put("installment_details", prepareLabels(application));
		
		return selectedLoan;
	}
	
	public static SelectedLoanDTO prepareSelectedLoanDTO(LendingApplication application) {
		SelectedLoanDTO selectedLoan = new SelectedLoanDTO();
		
		selectedLoan.setId(application.getId());
		selectedLoan.setAmount(application.getLoanAmount());
		selectedLoan.setCategory(application.getCategory());
		selectedLoan.setConstruct(application.getLoanConstruct());
		selectedLoan.setTenure(application.getTenure());
		selectedLoan.setFincanceCharge(application.getProcessingFee());
		selectedLoan.setEdi(application.getEdi());
		selectedLoan.setEdiDuration(application.getPayableDays());
		selectedLoan.setInterestRate(application.getInterestRate());
		selectedLoan.setRepayment(application.getRepayment());
		selectedLoan.setDisbursementAmount(application.getLoanAmount() - application.getProcessingFee());
		selectedLoan.setInterestAmount(application.getRepayment() - application.getLoanAmount());
		selectedLoan.setInstallmentDetails(prepareLabels(application));
		
		return selectedLoan;
	}
	
	public static Map<String, Object> prepareShopDetailsForClient(LendingApplication application) {
		Map<String, Object> shopDetails = new LinkedHashMap<>();
		
		shopDetails.put("business_name", application.getBusinessName());
		shopDetails.put("shop_number", application.getShopNumber());
		shopDetails.put("street_address", application.getStreetAddress());
		shopDetails.put("area", application.getArea());
		shopDetails.put("landmark", application.getLandmark());
		shopDetails.put("pincode", application.getPincode());
		shopDetails.put("city", application.getCity());
		shopDetails.put("state", application.getState());
		
		return shopDetails;
	}
	
	public static ShopDetailsDTO prepareShopDetailsDTO(LendingApplication application) {
		ShopDetailsDTO shopDetails = new ShopDetailsDTO();
		
		shopDetails.setBusinessName(application.getBusinessName());
		shopDetails.setShopNumber(application.getShopNumber());
		shopDetails.setStreetAddress(application.getStreetAddress());
		shopDetails.setArea(application.getArea());
		shopDetails.setLandmark(application.getLandmark());
		if(application.getPincode() != null) {
			shopDetails.setPincode(application.getPincode().toString());
		}
		shopDetails.setCity(application.getCity());
		shopDetails.setState(application.getState());
		
		return shopDetails;
	}
	
	private static List<LabelDTO> prepareLabels(LendingApplication application) {
		List<LabelDTO> list = new ArrayList<>();
		
		if("CONSTRUCT_1".equals(application.getLoanConstruct())) {
			
		} else if("CONSTRUCT_2".equals(application.getLoanConstruct())) {
			list.add(new LabelDTO("EDI for 1st Month", "ZERO"));
			list.add(new LabelDTO("EDI for Next " + (Integer.valueOf(application.getTenureInMonths()) - 1) + " Month", "₹" + application.getEdi() + "/day"));
		} else if("CONSTRUCT_3".equals(application.getLoanConstruct())) {
			list.add(new LabelDTO("EDI for 1st Month", "₹" + application.getIoEdi() + "/day"));
			list.add(new LabelDTO("EDI for Next " + (Integer.valueOf(application.getTenureInMonths()) - 1) + " Month", "₹" + application.getEdi() + "/day"));
		} else {
			logger.error("Construct {} not defined, throwing Exception", application.getLoanConstruct());
			throw new RuntimeException("Construct not defined.");
		}
		
		return list;
	}
}
