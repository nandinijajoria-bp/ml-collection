package com.bharatpe.lending.controller;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bharatpe.lending.dto.PincodeVerifyDTO;
import com.bharatpe.lending.service.PincodeVerificationServices;
import com.bharatpe.lending.service.TopupLoanEligibleService;

@RestController
@RequestMapping("lending/common/*")
public class CommonController {
	
	private Logger logger = LoggerFactory.getLogger(CommonController.class);
	
	@Autowired
	TopupLoanEligibleService topupLoanEligibleService;
	
	
	@Autowired
	PincodeVerificationServices pincodeVerify;

	@RequestMapping(value="/generateTopupLoan", method = RequestMethod.GET, consumes="application/json", produces="application/json")
	public ResponseEntity initiateEnach(@RequestParam(name = "mid") Long merchantId) {
		try {
			topupLoanEligibleService.generateTopupLoan(merchantId);
		} catch (Exception e) {
			logger.error("Exception while initiating enach", e);
		}
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@RequestMapping(value="/pincode/verify", method=RequestMethod.GET)
	public PincodeVerifyDTO verifyPinCodeForLoanEligibility(@RequestParam(name = "pincode") Integer pincode,HttpServletResponse response){
		return pincodeVerify.checkPincodeValidity(pincode);
	}
}