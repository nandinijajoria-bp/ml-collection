package com.bharatpe.lending.service;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.dao.LendingApplicationDao;

@Service
public class LendingUploadService {
	private Logger logger = LoggerFactory.getLogger(LendingUploadService.class);
	
	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	public Map<String, Object> runService(HttpServletRequest request, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
		Map<String, Object> resp = new LinkedHashMap<> ();
		LendingApplication lendingApplicationToSave;
		
		Long merchantId = Long.parseLong(request.getAttribute("merchantId").toString());
		
		Long applicationId =  commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
		int loanStep = commonAPIRequest.getPayload().get("loan_step") != null ? Integer.parseInt(commonAPIRequest.getPayload().get("loan_step").toString()) : 0;
		
		Map<String, String> selectedLoan = (Map<String, String>) commonAPIRequest.getPayload().get("selected_loan");
		
		Map<String, String> shopDetails = (Map<String, String>) commonAPIRequest.getPayload().get("shop_details");
		
		
		if(loanStep == 1 && shopDetails != null) {
			LendingApplication lendingApplication = lendingApplicationDao.fetchApplicationByIdAndStatus(applicationId, merchantId);
			if(applicationId != null && lendingApplication != null) {//update
				lendingApplicationToSave = prepareInputToSave(lendingApplication, shopDetails);
			}else {//insert
				lendingApplicationToSave = prepareInputToSave(merchantId, selectedLoan, shopDetails);
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
	
	private LendingApplication prepareInputToSave(LendingApplication lendingApplication, Map<String, String> shopDetails) {
		LendingApplication data = new LendingApplication();
		
		data.setStatus("draft");
		data.setApplicationId(lendingApplication.getApplicationId());
		data.setEmail(lendingApplication.getEmail());
		data.setMerchantId(lendingApplication.getMerchantId());
		data.setLoanAmount(lendingApplication.getLoanAmount());
		data.setCategory(lendingApplication.getCategory());
		data.setProcessingFee(lendingApplication.getProcessingFee());
		data.setEdi(lendingApplication.getEdi());
		data.setInterestRate(lendingApplication.getInterestRate());
		data.setRepayment(lendingApplication.getRepayment());
		data.setTenure(lendingApplication.getTenure());
		data.setPayableDays(lendingApplication.getPayableDays());
		
		data.setBusinessName(shopDetails.get("business_name"));
		data.setShopNumber(shopDetails.get("shop_number"));
		data.setStreetAddress(shopDetails.get("street_address"));
		data.setArea(shopDetails.get("area"));
		data.setLandmark(shopDetails.get("landmark"));
		data.setPincode(Long.parseLong(shopDetails.get("pincode")));
		data.setCity(shopDetails.get("city"));
		data.setState(shopDetails.get("state"));
		
		return data;
	}
	
	private LendingApplication prepareInputToSave(Long merchantId, Map<String, String> selectedLoan, Map<String, String> shopDetails) {
		LendingApplication data = new LendingApplication();
		
		data.setStatus("draft");
		data.setMerchantId(merchantId);
		data.setLoanAmount(Double.parseDouble(selectedLoan.get("amount")));
		data.setCategory(selectedLoan.get("category"));
		data.setProcessingFee(Double.parseDouble(selectedLoan.get("amount")));
		data.setEdi(Double.parseDouble(selectedLoan.get("edi")));
		data.setInterestRate(Double.parseDouble(selectedLoan.get("interest_rate")));
		data.setRepayment(Double.parseDouble(selectedLoan.get("repayment")));
		data.setTenure(selectedLoan.get("edi_duration"));
		data.setPayableDays(Long.parseLong(selectedLoan.get("duration")));

		data.setBusinessName(shopDetails.get("business_name"));
		data.setShopNumber(shopDetails.get("shop_number"));
		data.setStreetAddress(shopDetails.get("street_address"));
		data.setArea(shopDetails.get("area"));
		data.setLandmark(shopDetails.get("landmark"));
		data.setPincode(Long.parseLong(shopDetails.get("pincode")));
		data.setCity(shopDetails.get("city"));
		data.setState(shopDetails.get("state"));
		
		return data;
	}
	
	private Map<String, Object> prepareAPIResponse(LendingApplication lendingApplicationToSave) {
		Map<String, Object> response = new LinkedHashMap<>();
		Map<String, Object> loanApplication = new LinkedHashMap<>();
		Map<String, Object> selectedLoan = new LinkedHashMap<>();
		Map<String, Object> shopDetails = new LinkedHashMap<>();
		
		response.put("application_id", lendingApplicationToSave.getApplicationId());
		response.put("application_status", "draft");
		
		selectedLoan.put("loan_amount",lendingApplicationToSave.getLoanAmount());
		selectedLoan.put("edi",lendingApplicationToSave.getEdi());
		selectedLoan.put("edi_duration",lendingApplicationToSave.getPayableDays());
		selectedLoan.put("processing_fee",lendingApplicationToSave.getProcessingFee());
		selectedLoan.put("interest_rate",lendingApplicationToSave.getInterestRate());
		selectedLoan.put("repayment",lendingApplicationToSave.getRepayment());
		selectedLoan.put("tenure",lendingApplicationToSave.getTenure());
		selectedLoan.put("category",lendingApplicationToSave.getCategory());
		
		shopDetails.put("business_name",lendingApplicationToSave.getBusinessName());
		shopDetails.put("shop_number",lendingApplicationToSave.getShopNumber());
		shopDetails.put("street_address",lendingApplicationToSave.getStreetAddress());
		shopDetails.put("area",lendingApplicationToSave.getArea());
		shopDetails.put("landmark",lendingApplicationToSave.getLandmark());
		shopDetails.put("pincode",lendingApplicationToSave.getPincode());
		shopDetails.put("city",lendingApplicationToSave.getCity());
		shopDetails.put("state",lendingApplicationToSave.getState());
		
		loanApplication.put("selected_loan", selectedLoan);
		loanApplication.put("shop_details",shopDetails);
		
		response.put("loan_application", loanApplication);
		
		return response;
	}
}
