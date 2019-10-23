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
import com.bharatpe.lending.service.VerifyOTPService;

@RestController
@RequestMapping("lending")
public class VerifyOTPController {
	Logger logger = LoggerFactory.getLogger(VerifyOTPController.class);

	@Autowired
	VerifyOTPService verifyOTPService;
	
	@RequestMapping(value="/verifyOTP", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Object verifyOTP(@RequestAttribute Merchant merchant, @RequestBody CommonAPIRequest commonAPIRequest) {
		logger.info("verifyOTP request : {}",commonAPIRequest);
		
		Object resp = verifyOTPService.verifyOTP(merchant, commonAPIRequest);
		
		logger.info("verifyOTP response : {}", resp);
		return resp;
	}

}
