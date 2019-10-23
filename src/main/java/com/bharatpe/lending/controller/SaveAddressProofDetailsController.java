package com.bharatpe.lending.controller;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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
import com.bharatpe.lending.service.SaveAddressProofDetailsService;

@RestController
@RequestMapping("lending/csPanel")
public class SaveAddressProofDetailsController {
		Logger logger = LoggerFactory.getLogger(SaveAddressProofDetailsController.class);
		
		@Autowired
		SaveAddressProofDetailsService saveAddressProofDetailsService;
		
		@RequestMapping(value="/saveAddressProofDetails", method = RequestMethod.POST, consumes="application/json", produces="application/json")
		public Object saveAddressProofDetails(HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
			Object finalResponse = new HashMap<>();
			logger.info("saveAddressProofDetails request : {}",commonAPIRequest);
			
			Map<String, String> validationResponse = validateInput(commonAPIRequest);
			
			if(validationResponse.get("response").equals("success")) {
				finalResponse = saveAddressProofDetailsService.saveAddressProofDetails(commonAPIRequest);
			}else {
				logger.info("SaveAddressProofDetailsService invalid request");
				response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
				finalResponse = validationResponse;
			}
			
			logger.info("saveAddressProofDetails response : {}", finalResponse);
			return finalResponse;
		}
		
		private Map<String, String> validateInput(CommonAPIRequest commonAPIRequest) {
			Map<String, String> response = new HashMap<>();
			response.put("response", "success");
			String missingMendatoryFields = "";
			
			Long applicationId =  (commonAPIRequest.getPayload().get("application_id") != null && !commonAPIRequest.getPayload().get("application_id").toString().isEmpty()) ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
			Long merchantId =  (commonAPIRequest.getPayload().get("merchant_id") != null && !commonAPIRequest.getPayload().get("merchant_id").toString().isEmpty()) ? Long.parseLong(commonAPIRequest.getPayload().get("merchant_id").toString()) : null;
			Map<String, String> addressProofDetails = commonAPIRequest.getPayload().get("address_proof_details") != null ? (Map<String, String>)commonAPIRequest.getPayload().get("address_proof_details") : null;
			
			String docNumber = null;
			String fatherName = null;
			String personName = null;
			String dob = null;
			
			if(addressProofDetails != null) {
				docNumber =  (addressProofDetails.get("doc_no") != null && !addressProofDetails.get("doc_no").isEmpty()) ? addressProofDetails.get("doc_no") : null;
				fatherName =  (addressProofDetails.get("father_name") != null && !addressProofDetails.get("father_name").isEmpty()) ? addressProofDetails.get("father_name") : null;
				personName =  (addressProofDetails.get("person_name") != null && !addressProofDetails.get("person_name").isEmpty()) ? addressProofDetails.get("person_name") : null;
				dob =  (addressProofDetails.get("dob") != null && !addressProofDetails.get("dob").isEmpty()) ? addressProofDetails.get("dob").toString() : null;
			}
			
			if(applicationId == null) {
				response.put("response", "failed");
				missingMendatoryFields += "Application Id,";
			}
			if(merchantId == null) {
				response.put("response", "failed");
				missingMendatoryFields += "Merchant Id,";
			}
			if(docNumber == null || docNumber.isEmpty()) {
				response.put("response", "failed");
				missingMendatoryFields += "Doc Number,";
			}
			if(fatherName == null || fatherName.isEmpty()) {
				response.put("response", "failed");
				missingMendatoryFields += "Father Name,";
			}
			if(personName == null || personName.isEmpty()) {
				response.put("response", "failed");
				missingMendatoryFields += "Person Name,";
			}
			if(dob == null || dob.isEmpty() || !isValidDateFormat(dob)) {
				response.put("response", "failed");
				missingMendatoryFields += "DOB,";
			}
			
			if(response.get("response").equals("failed")) {
				response.put("message", "Missing or Invalid Mendatory Fields : " + missingMendatoryFields.replaceAll(",$",""));
			}
			
			return response;
		}
		
		private Boolean isValidDateFormat(String dob) {
			Boolean flag = true;
			
			try {
				SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
				Date formattedDob = formatter.parse(dob.trim());
				if (!formatter.format(formattedDob).equals(dob.trim())) {
					flag = false;
				}
			} catch (ParseException e) {
				e.printStackTrace();
				logger.info("SaveAddressProofDetailsController date parsing error date_input : {}", dob);
			}

			return flag;
		}
}
