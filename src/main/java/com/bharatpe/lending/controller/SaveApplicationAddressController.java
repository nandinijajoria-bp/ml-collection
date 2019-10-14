package com.bharatpe.lending.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.service.SaveApplicationAddressService;

@RestController
@RequestMapping("lending/csPanel")
public class SaveApplicationAddressController {
		Logger logger = LoggerFactory.getLogger(SaveApplicationAddressController.class);
		
		@Autowired
		SaveApplicationAddressService saveApplicationAddressService;
		
		@RequestMapping(value="/saveApplicationAddress", method = RequestMethod.POST, consumes="application/json", produces="application/json")
		public Map<String, String> saveApplicationAddress(HttpServletRequest request, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
			Instant start = Instant.now();
			Map<String, String> finalResponse = new LinkedHashMap<>();
			logger.info("saveApplicationAddress request : {}",commonAPIRequest);
			
			Map<String, String> validationResponse = validateInput(commonAPIRequest);
			
			if(validationResponse.get("response").equals("success")) {
				finalResponse = saveApplicationAddressService.runService(request, response, commonAPIRequest);
			}else {
				logger.info("SaveAddressProofDetailsService invalid request");
				response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
				finalResponse = validationResponse;
			}
			
			logger.info("saveApplicationAddress response : {}", finalResponse);
			Instant end = Instant.now();
			logger.info("Time Taken by saveApplicationAddress API : {} miliseconds", Duration.between(start, end).toMillis());
			return finalResponse;
		}
		
		private Map<String, String> validateInput(CommonAPIRequest commonAPIRequest) {
			Map<String, String> response = new LinkedHashMap<>();
			response.put("response","success");
			String missingFields = "";
			
			Long applicationId =  commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
			Long merchantId =  commonAPIRequest.getPayload().get("merchant_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("merchant_id").toString()) : null;
			Map<String, String> shopDetails =  commonAPIRequest.getPayload().get("shop_details") != null ? (Map<String, String>)commonAPIRequest.getPayload().get("shop_details") : null;
			
			if(applicationId == null) {
				response.put("response","failed");
				missingFields += "Application Id,";
			}
			if(merchantId == null) {
				response.put("response","failed");
				missingFields += "Merchant Id,";
			}
			if(shopDetails != null) {
				if(shopDetails.get("shop_number") == null || shopDetails.get("shop_number").isEmpty()) {
					response.put("response","failed");
					missingFields += "Shop Number,";
				}
				if(shopDetails.get("street_address") == null || shopDetails.get("street_address").isEmpty()) {
					response.put("response","failed");
					missingFields += "Street Address,";
				}
				if(shopDetails.get("area") == null || shopDetails.get("area").isEmpty()) {
					response.put("response","failed");
					missingFields += "Area,";
				}
				if(shopDetails.get("pincode") == null || shopDetails.get("pincode").isEmpty()) {
					response.put("response","failed");
					missingFields += "pincode,";
				}
				if(shopDetails.get("city") == null || shopDetails.get("city").isEmpty()) {
					response.put("response","failed");
					missingFields += "City";
				}
				if(shopDetails.get("state") == null || shopDetails.get("state").isEmpty()) {
					response.put("response","failed");
					missingFields += "State";
				}
			}else {
				response.put("response","failed");
				missingFields += "Shop Details,";
			}
			
			
			if(response.get("response").equals("failed")) {
				response.put("error","Missing or Empty Mendatory Fields : " + missingFields.replaceAll(",$",""));
			}
			
			return response;
		}
}
