package com.bharatpe.lending.controller;

import com.bharatpe.lending.dto.IneligibleRequestDTO;
import com.bharatpe.lending.dto.LendingActiveLoansResponseDTO;
import com.bharatpe.lending.dto.LendingOffersResponseDTO;
import com.bharatpe.lending.dto.LoanDetailsResponseDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.dto.SettlementResponseDTO;
import com.bharatpe.lending.service.ActiveLoansService;
import com.bharatpe.lending.service.ImageURLService;
import com.bharatpe.lending.service.LendingAgreementService;
import com.bharatpe.lending.service.LendingOffersService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.service.LoanDetailsService;

import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("lending")
@CrossOrigin(origins = "*")
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
	ActiveLoansService activeLoansService;

	@RequestMapping(value="/loanDetails", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public ResponseEntity<LoanDetailsResponseDTO> loanDetails(@RequestAttribute Merchant merchant, @RequestAttribute String clientIp, HttpServletResponse response, @RequestBody(required = false) RequestDTO<IneligibleRequestDTO> requestDTO) {
		logger.info("loanDetails request : {}", requestDTO);

		LoanDetailsResponseDTO resp = loanDetailsService.fetchLoanDetails(merchant, requestDTO, clientIp);
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
	public ResponseEntity<SettlementResponseDTO> settlement(@RequestAttribute Merchant merchant) {
		try {
			return new ResponseEntity<>(loanDetailsService.getSettlements(merchant), HttpStatus.OK);
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
		return new ResponseEntity<>(activeLoansService.getActiveLoans(requestMerchantId, requestMerchantStoreId), HttpStatus.OK);

	}

	@RequestMapping(value = "/get_offers", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
	public ResponseEntity<LendingOffersResponseDTO> getAvailableLoans(@RequestAttribute Merchant merchant) {
		logger.info("LendingOffers request with merchant_id : {}", merchant.getId());
		return new ResponseEntity<>(lendingOffersService.getOffers(merchant.getId()), HttpStatus.OK);
	}
}
