package com.bharatpe.lending.controller;

import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.service.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.objects.CommonAPIRequest;

import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("lending")
public class LoanDetailsController {
	Logger logger = LoggerFactory.getLogger(LoanDetailsController.class);
	
	@Autowired
	LoanDetailsService loanDetailsService;

	@Autowired
	LendingAgreementService lendingAgreementService;

	@Autowired
	LendingOffersService lendingOffersService;

	@Autowired
	ImageURLService imageURLService;

	@Autowired
	MerchantLoansService merchantLoansService;
	
	@Autowired
	VerifyDocService verifyDocService;

	@Autowired
	LoanEligibleService loanEligibleService;

	@Autowired
	MerchantUpdateService merchantUpdateService;

	@RequestMapping(value="/loanDetails", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public ResponseEntity<LoanDetailsResponseDTO> loanDetails(@RequestAttribute Merchant merchant, @RequestAttribute String clientIp, HttpServletResponse response, @RequestBody(required = false) RequestDTO<IneligibleRequestDTO> requestDTO, @RequestHeader("token") String token) {
		logger.info("loanDetails request : {}", requestDTO);

		LoanDetailsResponseDTO resp = loanDetailsService.fetchLoanDetails(merchant, requestDTO, clientIp, token);
		if (resp == null){
			logger.info("Sending gateway timeout for merchant: {}", merchant.getId());
			LoanDetailsResponseDTO response1 = new LoanDetailsResponseDTO();
			response1.setSuccess(false);
			response1.setMessage("Experian Failed");
			return new ResponseEntity<>(response1, HttpStatus.OK);
		}
		logger.info("loanDetails response : {}", resp);
		return new ResponseEntity<>(resp, HttpStatus.OK);
	}

	@RequestMapping(value="/lendingAgreement", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Object lendingAgreement(@RequestAttribute Merchant merchant, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {

		Object resp = lendingAgreementService.fetchLendingAgreement(merchant, response, commonAPIRequest);

		logger.info("LendingAgreement response : {}", resp);
		return resp;
	}

	@RequestMapping(value="/imageURL", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Object imageURL(@RequestAttribute Merchant merchant, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
		logger.info("ImageURLController request : {}",commonAPIRequest);

		Object resp = imageURLService.fetchAndWrapResult(merchant, commonAPIRequest);

		logger.info("ImageURLController response : {}", resp);
		return resp;
	}

	@RequestMapping(value="/settlement", method = RequestMethod.GET, consumes="application/json", produces="application/json")
	public ResponseEntity<SettlementResponseDTO> settlement(@RequestAttribute Merchant merchant, @RequestParam(name = "loan_id", required = false) Long loanId) {
		try {
			return new ResponseEntity<>(loanDetailsService.getSettlements(merchant, loanId), HttpStatus.OK);
		} catch (Exception e) {
			logger.error("Exception in settlement---", e);
			return new ResponseEntity<>(new SettlementResponseDTO(false, "Something went wrong"), HttpStatus.OK);
		}
	}

	@RequestMapping(value = "/active_loans", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
	public ResponseEntity<LendingActiveLoansResponseDTO> getAvailableLoans(
			@RequestParam(name = "merchant_id") Long requestMerchantId,
			@RequestParam(name = "merchant_store_id", required = false) Long requestMerchantStoreId) {
		logger.info("activeLoans request with merchant_id : {}, merchant_store_id: {}", requestMerchantId,
				requestMerchantStoreId);
		return new ResponseEntity<>(merchantLoansService.getActiveLoans(requestMerchantId, requestMerchantStoreId), HttpStatus.OK);

	}

	@RequestMapping(value = "/get_offers", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
	public ResponseEntity<LendingOffersResponseDTO> getAvailableLoans(@RequestAttribute Merchant merchant) {
		logger.info("LendingOffers request with merchant_id : {}", merchant.getId());
		return new ResponseEntity<>(lendingOffersService.getOffers(merchant.getId()), HttpStatus.OK);
	}

	@RequestMapping(value = "/verify_pan_card/{panCard}",method = RequestMethod.GET)
	public VerifyPanCardDto verifyPanCard(@RequestAttribute Merchant merchant,@PathVariable("panCard") String panCard) {
		logger.info("verify pancard check request for merchant:{} and pancard:{}", merchant.getId(), panCard);
		VerifyPanCardDto verifyPanCardDto =  verifyDocService.verifyPanCard(merchant, panCard);
		logger.info("verify pancard check response for merchant:{} and pancard:{} is :{}", merchant.getId(), panCard, verifyPanCardDto);
		return verifyPanCardDto;
	}

	@RequestMapping(value = "/offers", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
	public ResponseEntity<LendingOffersResponseDTO> getSwipeOffers(@RequestParam(name = "merchant_id") Long requestMerchantId,
																   @RequestParam(name = "merchant_store_id", required = false) Long requestMerchantStoreId) {
		logger.info("LendingOffers request with merchant_id : {}", requestMerchantId);
		return new ResponseEntity<>(lendingOffersService.getOffers(requestMerchantId), HttpStatus.OK);
	}

	@RequestMapping(value = "/eligible_offers", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
	public ResponseEntity<EligibleLendingOffersResponseDTO> getEligibleOfferDetails(@RequestAttribute Merchant merchant,
			@RequestParam(name = "query_amount", required = true) Double queryAmount) {
		logger.info("EligibleLendingOffers request with merchant_id: {}, query_amount: {}", merchant.getId(), queryAmount);
		EligibleLendingOffersResponseDTO resp = loanEligibleService.getEligibilityDetails(merchant.getId(), queryAmount);
		logger.info("EligibleLendingOffers response: {}", resp);
		return new ResponseEntity<>(resp, HttpStatus.OK);
	}

	@RequestMapping(value = "/eligible_loan", method = RequestMethod.POST, consumes = "application/json", produces = "application/json")
	public ResponseEntity<ResponseDTO> updateEligibleLoanAmount(@RequestAttribute Merchant merchant, @RequestBody(required = false) EligibleLoanUpdateRequestDTO requestDTO) {
		logger.info("updateEligibleLoanAmount request with merchant_id: {}, with body: {}", merchant.getId(), requestDTO);
		return new ResponseEntity<>(loanEligibleService.updateEligibleLoan(merchant.getId(), requestDTO), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/derog_application", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
	public ResponseEntity<ApplicationDerogResponseDTO> derogMerchantExperian(@RequestParam(name = "merchant_id") Long merchantId,
	@RequestParam(name = "application_id") Long applicationId) {
		logger.info("derogMerchantExperian request with merchant_id: {}, applicationId: {}", merchantId, applicationId);
		return new ResponseEntity<>(loanEligibleService.processDerogSince(merchantId, applicationId, LendingConstants.APPLICATION_DEROG_RECHECK_MIN_DAYS), HttpStatus.OK);
	}

	@RequestMapping(value="/merchant_loans", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
	public ResponseEntity<LendingMerchantLoansResponseDTO> merchantLoans(@RequestAttribute Merchant merchant) {
		logger.info("merchantLoans request merchant_id: {}", merchant.getId());
		LendingMerchantLoansResponseDTO resp = merchantLoansService.getMerchantLoans(merchant.getId());
		logger.info("merchantLoans response : {}", resp);
		return new ResponseEntity<>(resp, HttpStatus.OK);
	}

	@RequestMapping(value="/algo360_logs", method=RequestMethod.POST, consumes="application/json", produces="application/json")
	public ResponseEntity<String> processAlgo360Logs(@RequestAttribute Merchant merchant, @RequestBody String data, @RequestHeader("token") String token){

		merchantUpdateService.saveAlgo360Logs(merchant, data);
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping(value = "/check_new_merchant")
	public ResponseEntity<CommonResponse> checkCoolOff(@RequestAttribute Merchant merchant, @RequestBody CoolOffRequestDTO requestDTO) {
		logger.info("check_new_merchant request:{} for merchantId: {}", requestDTO, merchant.getId());
		return new ResponseEntity<>(lendingOffersService.checkCoolOffPeriod(merchant, requestDTO), HttpStatus.OK);
	}

	@RequestMapping(value="/make_me_fresh", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
	public ResponseEntity<CommonResponse> fresh(@RequestAttribute Merchant merchant) {
		logger.info("make me fresh request merchant_id: {}", merchant.getId());
		lendingOffersService.makeMeFresh(merchant);
		return new ResponseEntity<>(new CommonResponse(true, "success"), HttpStatus.OK);
	}
}
