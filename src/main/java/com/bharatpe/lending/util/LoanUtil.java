package com.bharatpe.lending.util;

import java.util.*;
import java.util.concurrent.TimeUnit;

import com.bharatpe.common.entities.LendingCategories;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.utils.CurrencyUtils;
import com.bharatpe.lending.common.entity.CreditApplication;
import com.bharatpe.lending.common.entity.CreditApplicationAddress;
import com.bharatpe.lending.dto.LabelDTO;
import com.bharatpe.lending.dto.SelectedLoanDTO;
import com.bharatpe.lending.dto.ShopDetailsDTO;

public class LoanUtil {
	private static final Logger logger = LoggerFactory.getLogger(LoanUtil.class);

	public static Map<String, Object> prepareSelectedLoanForClient(LendingApplication application, LendingCategories lendingCategories) {
		Map<String, Object> selectedLoan = new LinkedHashMap<>();
		
		selectedLoan.put("amount", application.getLoanAmount().intValue());
		selectedLoan.put("category", application.getCategory());
		selectedLoan.put("construct", application.getLoanConstruct());
		selectedLoan.put("tenure", application.getTenure());
		selectedLoan.put("id", application.getId());
		selectedLoan.put("finance_charge", application.getProcessingFee().intValue());
		selectedLoan.put("edi", application.getEdi());
		selectedLoan.put("edi_duration", application.getPayableDays());
		selectedLoan.put("interest_rate", application.getInterestRate());
		selectedLoan.put("repayment", application.getRepayment().intValue());
		selectedLoan.put("disbursement_amount", application.getLoanAmount().intValue() - application.getProcessingFee().intValue());
		selectedLoan.put("interest_amount", application.getRepayment().intValue() - application.getLoanAmount().intValue());
		selectedLoan.put("installment_details", prepareLabels(application, lendingCategories.getIoTenureMonths().intValue()));
		
		return selectedLoan;
	}
	
	public static SelectedLoanDTO prepareSelectedLoanDTO(LendingApplication application, LendingCategories lendingCategories) {
		SelectedLoanDTO selectedLoan = new SelectedLoanDTO();
		
		selectedLoan.setId(application.getId());
		selectedLoan.setAmount(application.getLoanAmount().intValue());
		selectedLoan.setCategory(application.getCategory());
		selectedLoan.setConstruct(application.getLoanConstruct());
		selectedLoan.setTenure(application.getTenure());
		selectedLoan.setFincanceCharge(application.getProcessingFee().intValue());
		selectedLoan.setEdi(application.getEdi());
		selectedLoan.setEdiDuration(application.getPayableDays());
		selectedLoan.setInterestRate(application.getInterestRate());
		selectedLoan.setRepayment(application.getRepayment() != null ? application.getRepayment().intValue() : 0);
		selectedLoan.setDisbursementAmount((application.getLoanAmount().intValue() - application.getProcessingFee().intValue()));
		selectedLoan.setInterestAmount(application.getRepayment().intValue() - application.getLoanAmount().intValue());
		if (lendingCategories != null) {
			selectedLoan.setInstallmentDetails(prepareLabels(application, lendingCategories.getIoTenureMonths().intValue()));
		}
		
		return selectedLoan;
	}
	
	//for credit line
	public static SelectedLoanDTO prepareSelectedLoanDTO(CreditApplication application) {
		
		
		SelectedLoanDTO selectedLoan = new SelectedLoanDTO();
		
		selectedLoan.setId(application.getId());
		selectedLoan.setAmount(application.getAmount().intValue());
		selectedLoan.setCategory(application.getCategory());
		
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
		shopDetails.put("alternate_mobile", application.getAlternateMobile());
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

	//for credit line
	public static ShopDetailsDTO prepareShopDetailsDTO(CreditApplication application, CreditApplicationAddress creditApplicationAddress){

		ShopDetailsDTO shopDetails = new ShopDetailsDTO();
		if(creditApplicationAddress!=null) {
			shopDetails.setBusinessName(application.getBusinessName());
			shopDetails.setShopNumber(creditApplicationAddress.getShopNumber());
			shopDetails.setStreetAddress(creditApplicationAddress.getStreetAddress());
			shopDetails.setArea(creditApplicationAddress.getArea());
			shopDetails.setLandmark(creditApplicationAddress.getLandmark());
			if (creditApplicationAddress.getPincode() != null) {
				shopDetails.setPincode(creditApplicationAddress.getPincode().toString());
			}
			shopDetails.setCity(creditApplicationAddress.getCity());
			shopDetails.setState(creditApplicationAddress.getState());
			shopDetails.setAlternateContact(application.getAlternateMobile());
		}
		else {
			logger.warn("Shop details not available for application {}",application.getId());
		}
		return shopDetails;
	}
	
	private static List<LabelDTO> prepareLabels(LendingApplication application, int months) {
		List<LabelDTO> list = new ArrayList<>();
		
		if("CONSTRUCT_1".equals(application.getLoanConstruct())) {
			
		} else if("CONSTRUCT_2".equals(application.getLoanConstruct())) {
			list.add(new LabelDTO("EDI for 1st Month", "ZERO"));
			list.add(new LabelDTO("EDI for Next " + (application.getTenureInMonths() - 1) + " Months", "₹" + CurrencyUtils.formatInt(application.getEdi().intValue()) + "/day"));
			list.add(new LabelDTO("No Deduction on Sundays", ""));
		} else if("CONSTRUCT_3".equals(application.getLoanConstruct())) {
			if (months > 1) {
				list.add(new LabelDTO("EDI for 1st "+months+" Months", "₹" + CurrencyUtils.formatInt(application.getIoEdi().intValue()) + "/day"));
			} else {
				list.add(new LabelDTO("EDI for 1st Month", "₹" + CurrencyUtils.formatInt(application.getIoEdi().intValue()) + "/day"));
			}
			list.add(new LabelDTO("EDI for Next " + (application.getTenureInMonths() - months) + " Months", "₹" + CurrencyUtils.formatInt(application.getEdi().intValue()) + "/day"));
			list.add(new LabelDTO("No Deduction on Sundays", ""));
		} else {
			logger.error("Construct {} not defined, throwing Exception", application.getLoanConstruct());
			throw new RuntimeException("Construct not defined.");
		}
		
		return list;
	}

	public static long getDateDiffInDays(Date startTime, Date endTime) {
		long diff = endTime.getTime() - startTime.getTime();
		return TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);
	}

	public static Map<String , String> settlementMode = new HashMap<String , String>() {{
		put("SETTLEMENT", "QR Txns.");
		put("EXTERNAL_NACH", "NACH");
		put("INTEREST_ACCOUNT", "Interest A/c");
		put("REFUND", "Refund");
		put("BHARATPE_NACH", "NACH");
		put("UPI","UPI");
	}};

	public static int getEdiDays(int tenure){
		switch (tenure){
			case 1: return 26;
			case 3: return 77;
			case 6: return 155;
			case 9: return 234;
			case 12: return 311;
			default: return 388;//15 months
		}
	}
}
