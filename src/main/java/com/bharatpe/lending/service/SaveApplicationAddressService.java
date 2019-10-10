package com.bharatpe.lending.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.dao.LendingApplicationDao;

@Service
public class SaveApplicationAddressService {
	private Logger logger = LoggerFactory.getLogger(SaveApplicationAddressService.class);
	
	@Autowired
	MerchantDao merchantDao;
	
	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	public Map<String, String> runService(HttpServletRequest request, HttpServletResponse response, CommonAPIRequest commonAPIRequest) {
		Map<String, String> finalResponse = new LinkedHashMap<>();
		
		Long applicationId =  Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString());
		Long merchantId = Long.parseLong(commonAPIRequest.getPayload().get("merchant_id").toString());
		Map<String, String> shopDetails = (Map<String, String>)commonAPIRequest.getPayload().get("shop_details");
		
		Boolean validMerchantFlag = isValidMerchant(merchantId);
		
		if(validMerchantFlag) {
			Boolean validApplicationFlag = isValidApplication(merchantId, applicationId);
			if(validApplicationFlag) {
				int updateId = updateApplicationAddress(shopDetails, applicationId, merchantId);
				if(updateId > 0) {
					finalResponse.put("response", "success");
					finalResponse.put("message", "Application Address Details Updated Successfully!");
				}else {
					finalResponse.put("response", "failed");
					finalResponse.put("message", "Something Went Wrong!");
				}
			}else {
				logger.info("SaveApplicationAddressService invalid Application Id", applicationId);
				response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
				finalResponse.put("response","failed");
				finalResponse.put("message","Invalid Application Id");
			}
		}else {
			logger.info("SaveApplicationAddressService invalid Merchant Id", merchantId);
			response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
			finalResponse.put("response","failed");
			finalResponse.put("message","Invalid Merchant Id");
		}
		
		return finalResponse;
	}
	
	private Boolean isValidMerchant(Long merchantId) {
		Boolean response = false;
		
		Optional<Merchant> merchant = merchantDao.findById(merchantId);
		if(merchant.isPresent()) {
			response = true;
		}
		
		return response;
	}
	
	private Boolean isValidApplication(Long merchantId, Long applicationId) {
		Boolean response = false;
		
		LendingApplication application = lendingApplicationDao.findByApplicationIdAndMerchantId(applicationId, merchantId);
		if(application != null) {
			response = true;
		}
		
		return response;
	}
	
	int updateApplicationAddress(Map<String, String> shopDetails, Long applicationId, Long merchantId) {
		
		int updateId = lendingApplicationDao.updateApplicationAddress(shopDetails.get("shop_number"), shopDetails.get("street_address"), shopDetails.get("area"), Long.parseLong(shopDetails.get("pincode")), shopDetails.get("city"), shopDetails.get("state"), applicationId, merchantId);
		
		return updateId;
	}
}
