package com.bharatpe.lending.controller;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dto.CreditApplicationRequestDTO;
import com.bharatpe.lending.dto.CreditApplicationResponseDTO;
import com.bharatpe.lending.dto.CreditUploadDocumentRequestDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.dto.SignAgreementDTO;
import com.bharatpe.lending.dto.UploadDocumentRequestDTO;
import com.bharatpe.lending.dto.UploadDocumentResponseDTO;
import com.bharatpe.lending.service.CancelApplicationService;
import com.bharatpe.lending.service.CreditApplicationService;
import com.bharatpe.lending.service.CreditCancelApplicationService;
import com.bharatpe.lending.service.CreditImageURLService;
import com.bharatpe.lending.service.CreditSignAgreementService;
import com.bharatpe.lending.service.CreditVerifyOTPService;
import com.bharatpe.lending.service.SignAgreementService;
import com.bharatpe.lending.service.UploadDocumentCreditService;
import com.bharatpe.lending.service.VerifyOTPService; 

 
	@RestController
	@RequestMapping("lending/credit_line")
	public class CreditApplicationController {
		

		Logger logger = LoggerFactory.getLogger(CreditApplicationController.class);
		
		@Autowired
		CreditImageURLService creditImageURLService;
	 
		@Autowired
		CreditApplicationService creditApplicationService;
		 	@Autowired
		UploadDocumentCreditService uploadDocumentCreditService;
		 	
			@Autowired
			CreditSignAgreementService creditSignAgreementService;

			@Autowired
			CreditVerifyOTPService creditVerifyOTPService;

			@Autowired
			CreditCancelApplicationService creditCancelApplicationService;
		
		@RequestMapping(value="/createApplication", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	    public CreditApplicationResponseDTO createApplication(@RequestAttribute Merchant merchant, @RequestAttribute String clientIp, HttpServletResponse response, @RequestBody RequestDTO<CreditApplicationRequestDTO> requestDTO) {

	        if(requestDTO.getPayload().getPincode() != null && !creditApplicationService.checkLoanRequestPinCodeForLoanEligibilty((int)(long)requestDTO.getPayload().getPincode())) {
	            logger.info("This loan request was raised from the location whose pin code is not eligible for the loan");
	            CreditApplicationResponseDTO creditApplicationResponse=new CreditApplicationResponseDTO();
 	         creditApplicationResponse.setCode(LendingConstants.LOAN_APPLICATION_OGL_CODE);
	         creditApplicationResponse.setMessage(LendingConstants.LOAN_APPLICATION_OGL_MESSAGE);
	            creditApplicationResponse.setSuccess(false);
	            return creditApplicationResponse;
	        }

	        logger.info("Create Application request : {}",requestDTO);
	        if(requestDTO.getPayload() == null) {
	            logger.info("Invalid request parameters : {}", requestDTO);
	            response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
	            return null;
	        }
	        requestDTO.getMeta().setIp(clientIp);
	        CreditApplicationResponseDTO creditApplicationResponse = creditApplicationService.createApplication(merchant, requestDTO);
	        logger.info("Create Application response : {}", creditApplicationResponse);
      creditApplicationResponse.setCode(LendingConstants.LOAN_APPLICATION_SUCCESS_CODE);
     creditApplicationResponse.setMessage( LendingConstants.LOAN_APPLICATION_SUCCESS_MESSAGE);
	      return creditApplicationResponse;
	    }
		
		@RequestMapping(value="/uploadDocument", method = RequestMethod.POST, consumes="application/json", produces="application/json")
		public UploadDocumentResponseDTO uploadDocument(@RequestAttribute Merchant merchant, @RequestAttribute String clientIp, HttpServletResponse response, @RequestBody RequestDTO<CreditUploadDocumentRequestDTO> requestDTO) {
			logger.info("UploadDocument request : {}",requestDTO);
			if(requestDTO.getPayload() == null) {
				logger.info("Invalid request parameters : {}", requestDTO);
				response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
				return null;
			}
			requestDTO.getMeta().setIp(clientIp);
			UploadDocumentResponseDTO uploadDocumentResponse = uploadDocumentCreditService.uploadDocument(merchant, requestDTO);

			logger.info("UploadDocument response : {}", uploadDocumentResponse);
			return uploadDocumentResponse;
		}
	 
		@RequestMapping(value="/signAgreement", method = RequestMethod.POST, consumes="application/json", produces="application/json")
		public Object signAgreement(@RequestAttribute Merchant merchant, @RequestAttribute String clientIp,  @RequestBody RequestDTO<SignAgreementDTO> requestDTO) {
			logger.info("singAgreement request : {}",requestDTO);
			requestDTO.getMeta().setIp(clientIp);
			Object resp = creditSignAgreementService.signAgreement(merchant, requestDTO);
			logger.info("signAgreement response : {}", resp);
			return resp;
		}

		@RequestMapping(value="/verifyOTP", method = RequestMethod.POST, consumes="application/json", produces="application/json")
		public Object verifyOTP(@RequestAttribute Merchant merchant, @RequestAttribute String clientIp, @RequestBody CommonAPIRequest commonAPIRequest) {
			logger.info("verifyOTP request : {}",commonAPIRequest);
			commonAPIRequest.getMeta().setIp(clientIp);
			Object resp = creditVerifyOTPService.verifyOTP(merchant, commonAPIRequest);
			logger.info("verifyOTP response : {}", resp);
			return resp;
		}
		
		@RequestMapping(value="/cancelApplication", method = RequestMethod.POST, consumes="application/json", produces="application/json")
		public Object cancelApplication(@RequestAttribute Merchant merchant, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
			logger.info("cancelApplication request : {}",commonAPIRequest);
			Long applicationId =  commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;

			if(applicationId == null || applicationId <=0) {
				logger.info("CancelApplicationService invalid applicationId");
				response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
				Map<String, Boolean> resp = new HashMap<>();
				resp.put("success",false);
				return resp;
			}

			Object resp = creditCancelApplicationService.cancelApplication(merchant, applicationId);

			logger.info("cancelApplication response : {}", resp);
			return resp;
		}

		@RequestMapping(value="/sendOTP", method = RequestMethod.GET, consumes="application/json", produces="application/json")
		public ResponseEntity<ResponseDTO> sendOTP(@RequestAttribute Merchant merchant) {
			return new ResponseEntity<>(creditApplicationService.sendOtp(merchant), HttpStatus.OK);
		}
		
		@RequestMapping(value="/imageURL", method = RequestMethod.POST, consumes="application/json", produces="application/json")
		public Object imageURL(@RequestAttribute Merchant merchant, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
			logger.info("ImageURLController request : {}",commonAPIRequest);

			Object resp = creditImageURLService.fetchAndWrapResult(merchant, commonAPIRequest);

			logger.info("ImageURLController response : {}", resp);
			return resp;
		}
      
}





