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
import com.bharatpe.lending.service.VerifyApplicationKarzaStatusService;

@RestController
@RequestMapping("lending/csPanel")
public class VerifyApplicationKarzaStatusController {
	Logger logger = LoggerFactory.getLogger(VerifyApplicationKarzaStatusController.class);

	@Autowired
	VerifyApplicationKarzaStatusService verifyApplicationKarzaStatusService;
	
	@RequestMapping(value="/verifyApplicationKarzaStatus", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Map<String, String> verifyApplicationKarzaStatus(HttpServletRequest request, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
		Instant start = Instant.now();
		Map<String, String> resp = new LinkedHashMap<>();
		logger.info("verifyApplicationKarzaStatus request : {}",commonAPIRequest);
		
		Map<String, String> validationResponse = validateInput(commonAPIRequest);
		if(validationResponse.get("response").equals("success")) {
			resp = verifyApplicationKarzaStatusService.runService(request, response, commonAPIRequest);
		}else {
			logger.info("verifyApplicationKarzaStatus invalid request");
			response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
			resp = validationResponse;
		}
		
		logger.info("verifyApplicationKarzaStatus response : {}", resp);
		Instant end = Instant.now();
		logger.info("Time Taken by verifyApplicationKarzaStatus API : {} miliseconds", Duration.between(start, end).toMillis());
		return resp;
	}
	
	private Map<String, String> validateInput(CommonAPIRequest commonAPIRequest) {
		Map<String, String> response = new LinkedHashMap<>();
		response.put("response", "success");
		String missingFields = "";
		
		Long applicationId = null;
		Long merchantId = null;
		Long docId = null;
		
		if(commonAPIRequest.getPayload() != null) {
			applicationId = (commonAPIRequest.getPayload().get("application_id") == null) ? null : Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString());
			merchantId = (commonAPIRequest.getPayload().get("merchant_id") == null) ? null : Long.parseLong(commonAPIRequest.getPayload().get("merchant_id").toString());
			docId = (commonAPIRequest.getPayload().get("doc_id") == null) ? null : Long.parseLong(commonAPIRequest.getPayload().get("doc_id").toString());
		}
		
		if(applicationId == null) {
			response.put("response", "failed");
			missingFields += "Application Id,";
		}
		if(merchantId == null) {
			response.put("response", "failed");
			missingFields += "Merchant Id,";
		}
		if(docId == null) {
			response.put("response", "failed");
			missingFields += "Doc Id,";
		}
		
		if(response.get("response").equals("failed")) {
			response.put("message", "Mendatory Missing Fields : " + missingFields.replaceAll(",$",""));
		}
		
		return response;
	}
}
