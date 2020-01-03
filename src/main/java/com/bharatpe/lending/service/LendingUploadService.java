package com.bharatpe.lending.service;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.dao.AvailableLoanDao;
import com.bharatpe.common.entities.AvailableLoan;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingCategories;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.util.LoanUtil;

@Service
public class LendingUploadService {
	private Logger logger = LoggerFactory.getLogger(LendingUploadService.class);
	
	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	@Autowired
	AvailableLoanDao availableLoanDao;
	
	@Autowired
	LendingCategoryDao lendingCategoryDao;
	
	public Map<String, Object> uploadLoanDetails(Merchant merchant, HttpServletResponse response, CommonAPIRequest commonAPIRequest) {
		Map<String, Object> resp = new LinkedHashMap<> ();
		LendingApplication lendingApplicationToSave;
		
		Long merchantId = merchant.getId();
		
		Long applicationId =  commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
		int loanStep = commonAPIRequest.getPayload().get("loan_step") != null ? Integer.parseInt(commonAPIRequest.getPayload().get("loan_step").toString()) : 0;
		
		Map<String, String> selectedLoan = (Map<String, String>) commonAPIRequest.getPayload().get("selected_loan");
		Map<String, Object> shopDetails = (Map<String, Object>) commonAPIRequest.getPayload().get("shop_details");
		
		if(loanStep == 1 && shopDetails != null) {
			LendingApplication lendingApplication = lendingApplicationDao.fetchApplicationByIdAndStatus(applicationId, merchantId);
			AvailableLoan availableLoan = availableLoanDao.findByMerchantIdAndCategory(merchantId, selectedLoan.get("category"));
			if(availableLoan == null) {
				logger.info("No loan available for Merchant {} and category", merchantId, selectedLoan.get("category"));
				response.setStatus(Integer.parseInt(ResponseCode.NOT_FOUND));
				resp.put("success","false");
				return resp;
			}
			
			if(applicationId != null && lendingApplication != null) {//update
				lendingApplicationToSave = prepareInputToSave(lendingApplication, shopDetails);
			}else {//insert
				lendingApplicationToSave = prepareInputToSave(availableLoan, shopDetails);
			}
			lendingApplicationToSave.setLatitude(commonAPIRequest.getMeta().getLatitude());
			lendingApplicationToSave.setLongitude(commonAPIRequest.getMeta().getLongitude());
			lendingApplicationToSave.setIp(commonAPIRequest.getMeta().getIp());
			lendingApplicationDao.save(lendingApplicationToSave);
			resp = prepareAPIResponse(lendingApplicationToSave);
			logger.info("LendingUploadSerivce saved to lending_application : {}",lendingApplicationToSave);
		}else {
			logger.info("LendingUploadSerivce invalid request parameters : {}",commonAPIRequest);
			response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
			resp.put("success","false");
		}
		return resp;
	}
	
	private LendingApplication prepareInputToSave(LendingApplication lendingApplication, Map<String, Object> shopDetails) {
		lendingApplication.setStatus("draft");
		lendingApplication.setBusinessName(String.valueOf(shopDetails.get("business_name")));
		lendingApplication.setShopNumber(String.valueOf(shopDetails.get("shop_number")));
		lendingApplication.setStreetAddress(String.valueOf(shopDetails.get("street_address")));
		lendingApplication.setArea(String.valueOf(shopDetails.get("area")));
		lendingApplication.setLandmark(String.valueOf(shopDetails.get("landmark")));
		lendingApplication.setPincode(((Integer) shopDetails.get("pincode")).longValue());
		lendingApplication.setCity(String.valueOf(shopDetails.get("city")));
		lendingApplication.setState(String.valueOf(shopDetails.get("state")));
		
		return lendingApplication;
	}
	
	private LendingApplication prepareInputToSave(AvailableLoan availableLoan, Map<String, Object> shopDetails) {
		LendingApplication data = new LendingApplication();
		LendingCategories lendingCategory = lendingCategoryDao.findByCategory(availableLoan.getCategory()).get(0);
		Double edi = 0D;
		Double repayment = 0D;
		Double interestRate = 0D;
		Double ioEdi = 0D;
		Double processingFee = 0D;
		
		if("CONSTRUCT_2".equals(availableLoan.getLoanConstruct())) {
			Double processingFeeMultiplier = Double.valueOf(lendingCategory.getProcessingFee());
			processingFee = availableLoan.getAmount() * processingFeeMultiplier;
			
			Double totalInterest = Math.ceil(availableLoan.getAmount() * lendingCategory.getTenureMonths() * (lendingCategory.getInterestRate() / 100));
			edi = Math.ceil((availableLoan.getAmount() + totalInterest + processingFee) / lendingCategory.getPayableDays());
			repayment = Double.valueOf(Math.round(edi * lendingCategory.getPayableDays()));
			interestRate = (repayment - availableLoan.getAmount()) / (availableLoan.getAmount() * lendingCategory.getTenureMonths());
		} else if("CONSTRUCT_3".equals(availableLoan.getLoanConstruct())) {
			Double processingFeeMultiplier = Double.valueOf(lendingCategory.getProcessingFee());
			processingFee = availableLoan.getAmount() * processingFeeMultiplier;
			
			Double normalInterest = Math.ceil(availableLoan.getAmount() * lendingCategory.getTenureMonths() * (lendingCategory.getInterestRate() / 100));
			Double ioInterest = Math.ceil(availableLoan.getAmount() * lendingCategory.getIoTenureMonths() * (lendingCategory.getInterestRate() / 100));
			
			ioEdi = Math.ceil(ioInterest / lendingCategory.getIoPayableDays());
			edi = Math.ceil((availableLoan.getAmount() + normalInterest + processingFee) / lendingCategory.getPayableDays());
			
			repayment = Double.valueOf(Math.round( (edi * lendingCategory.getPayableDays()) + (ioEdi * lendingCategory.getIoPayableDays())));
			
			interestRate = (repayment - availableLoan.getAmount()) / ((availableLoan.getAmount() * lendingCategory.getTenureMonths()));
		} else {
			processingFee = Double.valueOf(lendingCategory.getProcessingFee());
			edi = Math.ceil((availableLoan.getAmount() + (availableLoan.getAmount() * (lendingCategory.getInterestRate() / 100) * lendingCategory.getTenureMonths()) + Integer.parseInt(lendingCategory.getProcessingFee())) / lendingCategory.getPayableDays());
			repayment = Double.valueOf(Math.round(lendingCategory.getPayableDays() * edi));
			interestRate = (((repayment - availableLoan.getAmount()) / availableLoan.getAmount()) / lendingCategory.getTenureMonths()) * 100;
		}
		
		data.setEdi(ioEdi);
		data.setRepayment(repayment);
		data.setInterestRate(interestRate);
		data.setIoEdi(ioEdi);
		data.setProcessingFee(processingFee);
		data.setStatus("draft");
		data.setMerchantId(availableLoan.getMerchantId());
		data.setLoanAmount(availableLoan.getAmount());
		data.setCategory(availableLoan.getCategory());
		data.setTenure(lendingCategory.getPayableConverter());
		data.setTenureInMonths(lendingCategory.getTenureMonths().intValue());
		data.setPayableDays(Long.valueOf(lendingCategory.getPayableDays()));
		data.setEdiFreeDays(lendingCategory.getEdiFreeDays());
		data.setIoPayableDays(lendingCategory.getIoPayableDays());
		data.setLoanConstruct(availableLoan.getLoanConstruct());

		data.setBusinessName(String.valueOf(shopDetails.get("business_name")));
		data.setShopNumber(String.valueOf(shopDetails.get("shop_number")));
		data.setStreetAddress(String.valueOf(shopDetails.get("street_address")));
		data.setArea(String.valueOf(shopDetails.get("area")));
		data.setLandmark(String.valueOf(shopDetails.get("landmark")));
		data.setPincode(((Integer)(shopDetails.get("pincode"))).longValue());
		data.setCity(String.valueOf(shopDetails.get("city")));
		data.setState(String.valueOf(shopDetails.get("state")));
		
		return data;
	}
	
	private Map<String, Object> prepareAPIResponse(LendingApplication lendingApplicationToSave) {
		Map<String, Object> response = new LinkedHashMap<>();
		Map<String, Object> loanApplication = new LinkedHashMap<>();
		
		response.put("application_id", lendingApplicationToSave.getApplicationId());
		response.put("application_status", "draft");
		
		loanApplication.put("selected_loan", LoanUtil.prepareSelectedLoanForClient(lendingApplicationToSave));
		loanApplication.put("shop_details",LoanUtil.prepareShopDetailsForClient(lendingApplicationToSave));
		
		response.put("loan_application", loanApplication);
		
		return response;
	}
	
}
