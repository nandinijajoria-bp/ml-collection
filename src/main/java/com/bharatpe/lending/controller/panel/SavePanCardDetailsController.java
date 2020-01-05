package com.bharatpe.lending.controller.panel;

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
import com.bharatpe.lending.service.SavePanCardDetailsService;

@RestController
@RequestMapping("lending/csPanel")
public class SavePanCardDetailsController {
	
	Logger logger = LoggerFactory.getLogger(SavePanCardDetailsController.class);
	
	@Autowired
	SavePanCardDetailsService savePanCardDetailsService;
	
	@RequestMapping(value="/savePanCardDetails", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Object savePanCardDetails(HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
		Object finalResponse = new HashMap<>();
		logger.info("savePanCardDetails request : {}",commonAPIRequest);
		
		Map<String, String> validationResponse = validateInput(commonAPIRequest);
		
		if(validationResponse.get("response").equals("success")) {
			finalResponse = savePanCardDetailsService.savePanCardDetails(commonAPIRequest);
		}else {
			logger.info("savePanCardDetails invalid request");
			response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
			finalResponse = validationResponse;
		}
		
		logger.info("savePanCardDetails response : {}", finalResponse);
		return finalResponse;
	}
	
	private Map<String, String> validateInput(CommonAPIRequest commonAPIRequest) {
		Map<String, String> response = new HashMap<>();
		response.put("response", "success");
		String missingMendatoryFields = "";
		
		Long applicationId =  (commonAPIRequest.getPayload().get("application_id") != null && !commonAPIRequest.getPayload().get("application_id").toString().isEmpty()) ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
		Long merchantId =  (commonAPIRequest.getPayload().get("merchant_id") != null && !commonAPIRequest.getPayload().get("merchant_id").toString().isEmpty()) ? Long.parseLong(commonAPIRequest.getPayload().get("merchant_id").toString()) : null;
		Map<String, String> panCardDetails = commonAPIRequest.getPayload().get("pancard_details") != null ? (Map<String, String>)commonAPIRequest.getPayload().get("pancard_details") : null;
		
		String docNumber = null;
		String fatherName = null;
		String personName = null;
		String dob = null;
		
		if(panCardDetails != null) {
			docNumber =  (panCardDetails.get("doc_no") != null && !panCardDetails.get("doc_no").isEmpty()) ? panCardDetails.get("doc_no") : null;
			fatherName =  (panCardDetails.get("father_name") != null && !panCardDetails.get("father_name").isEmpty()) ? panCardDetails.get("father_name") : null;
			personName =  (panCardDetails.get("person_name") != null && !panCardDetails.get("person_name").isEmpty()) ? panCardDetails.get("person_name") : null;
			dob =  (panCardDetails.get("dob") != null && !panCardDetails.get("dob").isEmpty()) ? panCardDetails.get("dob").toString() : null;
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
