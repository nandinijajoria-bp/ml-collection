package com.bharatpe.lending.controller;

import javax.servlet.http.HttpServletResponse;

import com.bharatpe.lending.common.query.dao.InternalClientDaoSlave;
import com.bharatpe.lending.common.query.entity.InternalClientSlave;
import com.bharatpe.lending.common.util.AesEncryptionUtil;
import com.bharatpe.lending.common.util.LendingHmacCalculator;
import com.bharatpe.lending.dto.CommonResponse;
import com.bharatpe.lending.dto.LendingCitiesResponseDTO;
import com.bharatpe.lending.dto.LendingPancardResponseDTO;
import com.bharatpe.lending.dto.LendingPincodesResponseDTO;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.service.ILendingCitiesService;
import com.bharatpe.lending.service.ILendingPancardService;
import com.bharatpe.lending.service.MerchantLoansService;
import com.bharatpe.lending.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
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
	InternalClientDaoSlave internalClientDaoSlave;

	@Autowired
	LendingHmacCalculator lendingHmacCalculator;

	@Autowired
	AesEncryptionUtil aesEncryptionUtil;

	@Autowired
	MerchantLoansService merchantLoansService;

	@Autowired
	PaymentService paymentService;

	@Autowired
	ILendingCitiesService iLendingCitiesService;

	@Autowired
	ILendingPancardService iLendingPancardService;


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
	public ResponseEntity<String> generateHash(@RequestBody Map<String, Object> requestMap, @RequestHeader(name = "clientName") String clientName){
		InternalClientSlave internalClient = internalClientDaoSlave.findByClientName(clientName);
		if (internalClient != null) {
			logger.info("lending secret:{}", aesEncryptionUtil.decrypt(internalClient.getSecret()));
			String hash = lendingHmacCalculator.calculateHmac(lendingHmacCalculator.getNestedPayload(requestMap), aesEncryptionUtil.decrypt(internalClient.getSecret()));
			return new ResponseEntity<>(hash, HttpStatus.OK);
		}
		return null;
	}

	@RequestMapping(value="/merchant", method=RequestMethod.GET)
	public ResponseEntity<CommonResponse> checkMerchant(@RequestParam(name = "mobile", required = false) String mobile, @RequestParam(name = "pancard", required = false) String pancard){
		return ResponseEntity.ok(merchantLoansService.checkMerchant(mobile, pancard));
	}

	@RequestMapping(value = "/lending_cities/active", method = RequestMethod.GET)
	public ResponseEntity<?> getActiveLendingCity(@RequestParam(name = "pincode") Integer pincode) {
		logger.info("getActiveLendingCity request with pincode : {} ", pincode);

		if (ObjectUtils.isEmpty(pincode) ) {
			return new ResponseEntity<>(new ApiResponse<>(false, "Required fields pincode not sent"),
			HttpStatus.BAD_REQUEST);
		}

		final LendingCitiesResponseDTO lendingCitiesResponseDTO = iLendingCitiesService.findActiveCityByPincode(pincode);

		if (ObjectUtils.isEmpty(lendingCitiesResponseDTO)) {
			return new ResponseEntity<>(new ApiResponse<>(false, "Lending city not found"),
			HttpStatus.NOT_FOUND);
		}

		return new ResponseEntity<>(new ApiResponse<>(lendingCitiesResponseDTO), HttpStatus.OK);

	}

	@RequestMapping(value = "/lending_pincode", method = RequestMethod.GET)
	public ResponseEntity<?> getLendingPincodeDetails(@RequestParam(name = "pincode") Integer pincode) {
		logger.info("getLendingPincodeDetails request with pincode : {} ", pincode);

		if (ObjectUtils.isEmpty(pincode) ) {
			return new ResponseEntity<>(new ApiResponse<>(false, "Required fields pincode not sent"),
			HttpStatus.BAD_REQUEST);
		}

		final LendingPincodesResponseDTO lendingPincodesResponseDTO = iLendingCitiesService.findByPincode(pincode);

		if (ObjectUtils.isEmpty(lendingPincodesResponseDTO)) {
			return new ResponseEntity<>(new ApiResponse<>(false, "Lending pincode details not found"),
			HttpStatus.NOT_FOUND);
		}

		return new ResponseEntity<>(new ApiResponse<>(lendingPincodesResponseDTO), HttpStatus.OK);

	}


	@RequestMapping(value = "/lending_pancard", method = RequestMethod.GET)
	public ResponseEntity<?> getLendingPancard(@RequestParam(name = "merchantId") Long merchantId) {
		logger.info("getLendingPancard request with merchantId : {} ", merchantId);

		if (ObjectUtils.isEmpty(merchantId) ) {
			return new ResponseEntity<>(new ApiResponse<>(false, "Required fields merchantId not sent"),
			HttpStatus.BAD_REQUEST);
		}

		final LendingPancardResponseDTO lendingPancardResponseDTO = iLendingPancardService.findByMerchantId(merchantId);

		if (ObjectUtils.isEmpty(lendingPancardResponseDTO)) {
			return new ResponseEntity<>(new ApiResponse<>(false, "Lending pancard not found"),
			HttpStatus.NOT_FOUND);
		}

		return new ResponseEntity<>(new ApiResponse<>(lendingPancardResponseDTO), HttpStatus.OK);
	}
}