package com.bharatpe.lending.controller;

import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.objects.CommonAPIRequest;

import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("lending")
public class LendingApplicationController {

	Logger logger = LoggerFactory.getLogger(LendingApplicationController.class);

	@Autowired
	LendingApplicationService lendingApplicationService;

	@Autowired
	UploadDocumentService uploadDocumentService;

	@Autowired
	SignAgreementService signAgreementService;

	@Autowired
	VerifyOTPService verifyOTPService;

	@Autowired
	CancelApplicationService cancelApplicationService;

	@RequestMapping(value="/createApplication", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public LendingApplicationResponse createApplication(@RequestAttribute Merchant merchant, @RequestAttribute String clientIp, HttpServletResponse response, @RequestBody RequestDTO<LendingApplicationRequest> requestDTO) {
		logger.info("Create Application request : {}",requestDTO);
		if(requestDTO.getPayload() == null) {
			logger.info("Invalid request parameters : {}", requestDTO);
			response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
			return null;
		}
		requestDTO.getMeta().setIp(clientIp);
		LendingApplicationResponse lendingApplicationResponse = lendingApplicationService.createApplication(merchant, requestDTO);
		logger.info("Create Application response : {}", lendingApplicationResponse);
		return lendingApplicationResponse;
	}

	@RequestMapping(value="/uploadDocument", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public UploadDocumentResponse uploadDocument(@RequestAttribute Merchant merchant, @RequestAttribute String clientIp, HttpServletResponse response, @RequestBody RequestDTO<UploadDocumentRequest> requestDTO) {
		logger.info("UploadDocument request : {}",requestDTO);
		if(requestDTO.getPayload() == null) {
			logger.info("Invalid request parameters : {}", requestDTO);
			response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
			return null;
		}
		requestDTO.getMeta().setIp(clientIp);
		UploadDocumentResponse uploadDocumentResponse = uploadDocumentService.uploadDocument(merchant, requestDTO);

		logger.info("UploadDocument response : {}", uploadDocumentResponse);
		return uploadDocumentResponse;
	}

	@RequestMapping(value="/signAgreement", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Object signAgreement(@RequestAttribute Merchant merchant, @RequestAttribute String clientIp, @RequestBody CommonAPIRequest commonAPIRequest) {
		logger.info("singAgreement request : {}",commonAPIRequest);
		commonAPIRequest.getMeta().setIp(clientIp);
		Object resp = signAgreementService.signAgreement(merchant, commonAPIRequest);
		logger.info("signAgreement response : {}", resp);
		return resp;
	}

	@RequestMapping(value="/verifyOTP", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Object verifyOTP(@RequestAttribute Merchant merchant, @RequestAttribute String clientIp, @RequestBody CommonAPIRequest commonAPIRequest) {
		logger.info("verifyOTP request : {}",commonAPIRequest);
		commonAPIRequest.getMeta().setIp(clientIp);
		Object resp = verifyOTPService.verifyOTP(merchant, commonAPIRequest);
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

		Object resp = cancelApplicationService.cancelApplication(merchant, applicationId);

		logger.info("cancelApplication response : {}", resp);
		return resp;
	}
}
