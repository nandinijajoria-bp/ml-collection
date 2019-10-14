package com.bharatpe.lending.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.service.UpdateLoanInfoFromPanelService;

@RestController
@RequestMapping("lending/csPanel")
public class UpdateLoanInfoFromPanelController {
	Logger logger = LoggerFactory.getLogger(UpdateLoanInfoFromPanelController.class);
	private List<String> lenderList = Arrays.asList("APOLLO","LIQUILOANS","MINTIFY","FLEXILOAN","NIYOGIN","HINDON");
	
	@Autowired
	UpdateLoanInfoFromPanelService updateLoanInfoFromPanelService;

	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	@Autowired
	MerchantDao merchantDao;
	
	@Autowired
	LendingPaymentScheduleDao lendingPaymentScheduleDao;
	
	@RequestMapping(value="/updateLoanInfoFromPanel", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Map<String, String> updateLoanInfoFromPanel(HttpServletRequest request, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
		Instant start = Instant.now();
		Map<String, String> resp = new LinkedHashMap<>();
		logger.info("updateLoanInfoFromPanel request : {}",commonAPIRequest);
		
		Map<String, String> validationResponse = validateInput(commonAPIRequest);
		if(validationResponse.get("response").equals("success")) {
			resp = updateLoanInfoFromPanelService.runService(request, response, commonAPIRequest);
		}else {
			logger.info("updateLoanInfoFromPanel invalid request");
			response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
			resp = validationResponse;
		}
		
		logger.info("updateLoanInfoFromPanel response : {}", resp);
		Instant end = Instant.now();
		logger.info("Time Taken by updateLoanInfoFromPanel API : {} miliseconds", Duration.between(start, end).toMillis());
		return resp;
	}
	
	private Map<String, String> validateInput(CommonAPIRequest commonAPIRequest) {
		Map<String, String> response = new LinkedHashMap<>();
		response.put("response","success");
		LendingApplication application = null;
		String missingFields = "";
		String invalidFields = "";
		
		Long merchantId =  commonAPIRequest.getPayload().get("merchant_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("merchant_id").toString()) : null;
		Long applicationId =  commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
		Long userId =  commonAPIRequest.getPayload().get("user_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("user_id").toString()) : null;
		Map<String, Object> loanDetails = (Map<String, Object>) commonAPIRequest.getPayload().get("loan_details");
		
		if(merchantId == null) {
			missingFields += "Merchant Id,";
		}else {
			Boolean validMerchantFlag = isValidMerchant(merchantId);
			if(validMerchantFlag == false) {
				invalidFields += "Merchant Id,";
			}
		}
		if(userId == null) {
			missingFields += "User Id,";
		}
		if(applicationId == null) {
			missingFields += "Application Id,";
		}else {
			application = lendingApplicationDao.findByApplicationIdAndMerchantId(applicationId, merchantId);
			if(application == null) {
				invalidFields += "Application Id,";
			}
		}
		
		if(loanDetails != null) {
			String lender = (loanDetails.get("lender") != null && !loanDetails.get("lender").toString().isEmpty()) ? loanDetails.get("lender").toString() : null;
			String physicalVerificationStatus = (loanDetails.get("physical_verification_status") != null && !loanDetails.get("physical_verification_status").toString().isEmpty()) ? loanDetails.get("physical_verification_status").toString() : null;
			String loanDisbursalStatus = (loanDetails.get("loan_disbursal_status") != null && !loanDetails.get("loan_disbursal_status").toString().isEmpty()) ? loanDetails.get("loan_disbursal_status").toString() : null;
			
			if(applicationId != null) {
				if(application.getLoanAmount() != null && application.getLoanAmount() > 25000 && physicalVerificationStatus == null) {
					missingFields += "Physical Verification Status,";
				}else if(application.getStatus() != null && application.getStatus().equalsIgnoreCase("approved") && lender == null) {
					missingFields += "Lender,";
				}
			}
			if(loanDisbursalStatus != null && lender == null || !lenderList.contains(lender)) {
				invalidFields += "Lender,";
			}
		}
		
		if(missingFields.isEmpty() && invalidFields.isEmpty()) {
			Boolean flag = checkActiveLoan(merchantId);
			if(flag) {
				response.put("response","failed");
				response.put("message","This Merchant has already an active Loan.");
			}
		}else {
			String message = "";
			if(!missingFields.isEmpty()) {
				message += "Missing Mendatory fields : ( " + missingFields.replaceAll(",$","") + " ) ,";
			}
			if(!invalidFields.isEmpty()) {
				message += "Invalid or Blank Mendatory fields : ( " + invalidFields.replaceAll(",$","") + " )";
			}
			response.put("response","failed");
			response.put("message", message.replaceAll(",$",""));
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
	
	private Boolean checkActiveLoan(Long merchantId) {
		Boolean flag = false;
		LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantId, "ACTIVE");
		if(lendingPaymentSchedule != null) {
			flag = true;
		}
		return flag;
	}
}
