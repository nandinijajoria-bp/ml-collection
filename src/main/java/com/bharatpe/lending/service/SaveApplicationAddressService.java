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
	
	Long applicationId;
	
	public Map<String, String> runService(HttpServletRequest request, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
		Map<String, String> finalResponse = new LinkedHashMap<>();
		
		this.applicationId =  commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
		Map<String, String> shopDetails =  commonAPIRequest.getPayload().get("shop_details") != null ? (Map<String, String>)commonAPIRequest.getPayload().get("shop_details") : null;

		Map<String, String> validationResponse = validateInput(shopDetails);
		
		if(validationResponse.get("response").equals("success")) {
			int updateId = updateApplicationAddress(shopDetails);
			if(updateId > 0) {
				finalResponse.put("response", "success");
				finalResponse.put("message", "Application Address Details Updated Successfully!");
			}else {
				finalResponse.put("response", "failed");
				finalResponse.put("message", "Something Went Wrong!");
			}
		}else {
			logger.info("SaveApplicationAddressService invalid request");
			response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
			finalResponse.put("response", validationResponse.get("response"));
			finalResponse.put("message", validationResponse.get("message"));
		}
		
		return finalResponse;
	}
	
	private Map<String, String> validateInput(Map<String, String> shopDetails) {
		Map<String, String> response = new LinkedHashMap<>();
		response.put("response", "success");
		response.put("message", "");
		if(shopDetails != null) {
			Long merchantId = (shopDetails.get("merchant_id") != null) ? Long.parseLong(shopDetails.get("merchant_id")) : null;
			if(merchantId != null) {
				Boolean validMerchantFlag = isValidMerchant(merchantId);
				if(validMerchantFlag) {
					Boolean validApplicationFlag = isValidApplication(merchantId);
					if(validApplicationFlag) {
						Map<String, Object> shopDetailsValidationResponse = isValidShopDetailsInput(shopDetails);
						if(!(Boolean)shopDetailsValidationResponse.get("status")) {
							response.put("response", "failed");
							response.put("message", shopDetailsValidationResponse.get("error").toString());
						}
					}else {
						response.put("response", "failed");
						response.put("message", "Invalid Application Id");
					}
				}else {
					response.put("response", "failed");
					response.put("message", "Invalid Merchant Id");
				}
			}else {
				response.put("response", "failed");
				response.put("message", "Empty Merchant Id");
			}
		}else {
			response.put("response", "failed");
			response.put("message", "Empty Shop Details");
		}
		
		return response;
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
	
	private Map<String, Object> isValidShopDetailsInput(Map<String, String> shopDetails) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("status",true);
		String missingFields = "";
		
		if(shopDetails.get("shop_number") == null || shopDetails.get("shop_number").isBlank()) {
			response.put("status",false);
			missingFields = "Shop Number,";
		}
		if(shopDetails.get("street_address") == null || shopDetails.get("street_address").isBlank()) {
			response.put("status",false);
			missingFields = "Street Address,";
		}
		if(shopDetails.get("area") == null || shopDetails.get("area").isBlank()) {
			response.put("status",false);
			missingFields = "Area,";
		}
		if(shopDetails.get("pincode") == null || shopDetails.get("pincode").isBlank()) {
			response.put("status",false);
			missingFields = "pincode,";
		}
		if(shopDetails.get("city") == null || shopDetails.get("city").isBlank()) {
			response.put("status",false);
			missingFields = "City";
		}
		if(shopDetails.get("state") == null || shopDetails.get("state").isBlank()) {
			response.put("status",false);
			missingFields = "State";
		}
		
		if((Boolean)response.get("status") == false) {
			response.put("error","Missing or Empty Mendatory Fields : " + missingFields.replaceAll(",$",""));
		}
		
		return response;
	}
	
	int updateApplicationAddress(Map<String, String> shopDetails) {
		Long merchantId = Long.parseLong(shopDetails.get("merchant_id"));
		
		int updateId = lendingApplicationDao.updateApplicationAddress(shopDetails.get("shop_number"), shopDetails.get("street_address"), shopDetails.get("area"), Long.parseLong(shopDetails.get("pincode")), shopDetails.get("city"), shopDetails.get("state"), this.applicationId, merchantId);
		
		return updateId;
	}
}
