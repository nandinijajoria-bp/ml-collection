package com.bharatpe.lending.controller;

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
import com.bharatpe.lending.service.SignAgreementService;

@RestController
@RequestMapping("lending")
public class SignAgreementController {

	Logger logger = LoggerFactory.getLogger(SignAgreementController.class);

	@Autowired
	SignAgreementService signAgreementService;
	
	@RequestMapping(value="/signAgreement", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Object signAgreement(@RequestAttribute Merchant merchant, @RequestBody CommonAPIRequest commonAPIRequest) {
		logger.info("singAgreement request : {}",commonAPIRequest);
		
		Object resp = signAgreementService.signAgreement(merchant, commonAPIRequest);
		
		logger.info("signAgreement response : {}", resp);
		return resp;
	}
}
