package com.bharatpe.lending.controller;

import javax.servlet.http.HttpServletResponse;

import com.bharatpe.common.dao.InternalClientDao;
import com.bharatpe.common.entities.InternalClient;
import com.bharatpe.common.utils.AesEncryption;
import com.bharatpe.common.utils.HmacCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.bharatpe.lending.dto.PincodeVerifyDTO;
import com.bharatpe.lending.service.PincodeVerificationServices;
import com.bharatpe.lending.service.TopupLoanEligibleService;

import java.util.Map;

@RestController
@RequestMapping("lending/common/*")
public class CommonController {
	
	private Logger logger = LoggerFactory.getLogger(CommonController.class);
	
	@Autowired
	TopupLoanEligibleService topupLoanEligibleService;
	
	
	@Autowired
	PincodeVerificationServices pincodeVerify;

	@Autowired
	InternalClientDao internalClientDao;

	@Autowired
	HmacCalculator hmacCalculator;

	@Autowired
	AesEncryption aesEncryption;

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

	@RequestMapping(value="/hash", method=RequestMethod.POST)
	public ResponseEntity<String> generateHash(@RequestBody Map<String, String> requestMap, @RequestHeader(name = "clientName") String clientName){
		InternalClient internalClient = internalClientDao.findByClientName(clientName);
		if (internalClient != null) {
			logger.info("lending secret:{}", aesEncryption.decrypt(internalClient.getSecret()));
			String hash = hmacCalculator.calculateHmac(hmacCalculator.getPayload(requestMap), aesEncryption.decrypt(internalClient.getSecret()));
			return new ResponseEntity<>(hash, HttpStatus.OK);
		}
		return null;
	}
}