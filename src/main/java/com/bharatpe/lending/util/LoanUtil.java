package com.bharatpe.lending.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.dto.LabelDTO;

public class LoanUtil {
	private static final Logger logger = LoggerFactory.getLogger(LoanUtil.class);

	public static Map<String, Object> prepareSelectedLoanForClient(LendingApplication application) {
		Map<String, Object> selectedLoan = new LinkedHashMap<>();
		
		selectedLoan.put("amount", application.getLoanAmount());
		selectedLoan.put("category", application.getCategory());
		selectedLoan.put("construct", application.getLoanConstruct());
		selectedLoan.put("tenure", application.getTenure());
		selectedLoan.put("id", application.getApplicationId());
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
