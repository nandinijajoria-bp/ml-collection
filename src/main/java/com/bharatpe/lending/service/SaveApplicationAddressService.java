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
import org.springframework.web.bind.annotation.RequestBody;

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
	
	private Long applicationId;
	private Long merchantId;
	
	public Map<String, String> runService(HttpServletRequest request, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
		Map<String, String> finalResponse = new LinkedHashMap<>();
		
		this.applicationId =  Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString());
		this.merchantId = Long.parseLong(commonAPIRequest.getPayload().get("merchant_id").toString());
		Map<String, String> shopDetails = (Map<String, String>)commonAPIRequest.getPayload().get("shop_details");
		
		Boolean validMerchantFlag = isValidMerchant(this.merchantId);
		
		if(validMerchantFlag) {
			Boolean validApplicationFlag = isValidApplication(this.merchantId);
			if(validApplicationFlag) {
				int updateId = updateApplicationAddress(shopDetails);
				if(updateId > 0) {
					finalResponse.put("response", "success");
					finalResponse.put("message", "Application Address Details Updated Successfully!");
				}else {
					finalResponse.put("response", "failed");
					finalResponse.put("message", "Something Went Wrong!");
				}
			}else {
				logger.info("SaveApplicationAddressService invalid Application Id", this.applicationId);
				response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
				finalResponse.put("response","failed");
				finalResponse.put("message","Invalid Application Id");
			}
		}else {
			logger.info("SaveApplicationAddressService invalid Merchant Id", this.merchantId);
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
	
	private Boolean isValidApplication(Long merchantId) {
		Boolean response = false;
		
		List<LendingApplication> applications = lendingApplicationDao.findByApplicationIdAndMerchantId(this.applicationId, merchantId);
		if(applications.size() > 0) {
			response = true;
		}
		
		return response;
	}
	
	int updateApplicationAddress(Map<String, String> shopDetails) {
		
		int updateId = lendingApplicationDao.updateApplicationAddress(shopDetails.get("shop_number"), shopDetails.get("street_address"), shopDetails.get("area"), Long.parseLong(shopDetails.get("pincode")), shopDetails.get("city"), shopDetails.get("state"), this.applicationId, this.merchantId);
		
		return updateId;
	}
}
