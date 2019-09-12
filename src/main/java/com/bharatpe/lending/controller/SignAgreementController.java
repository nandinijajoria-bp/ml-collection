package com.bharatpe.lending.controller;

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

import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.service.SignAgreementService;

@RestController
@RequestMapping("lending")
public class SignAgreementController {

	Logger logger = LoggerFactory.getLogger(SignAgreementController.class);

	@Autowired
	SignAgreementService signAgreementService;
	
	@RequestMapping(value="/signAgreement", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Map<String, Boolean> signAgreement(HttpServletRequest request, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
		logger.info("singAgreement request : {}",request);
		
		Map<String, Boolean> resp = signAgreementService.runService(request, response, commonAPIRequest);
		
		logger.info("signAgreement response : {}", resp);
		return resp;
	}
}
