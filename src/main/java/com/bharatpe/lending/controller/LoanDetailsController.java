package com.bharatpe.lending.controller;

import com.bharatpe.lending.dto.IneligibleRequestDTO;
import com.bharatpe.lending.dto.LoanDetailsResponseDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.service.ImageURLService;
import com.bharatpe.lending.service.LendingAgreementService;
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

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.service.LoanDetailsService;

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
	ImageURLService imageURLService;

	@RequestMapping(value="/loanDetails", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public ResponseEntity<LoanDetailsResponseDTO> loanDetails(@RequestAttribute Merchant merchant, @RequestAttribute String clientIp, HttpServletResponse response, @RequestBody(required = false) RequestDTO<IneligibleRequestDTO> requestDTO) {
		logger.info("loanDetails request : {}", requestDTO);

		LoanDetailsResponseDTO resp = loanDetailsService.fetchLoanDetails(merchant, requestDTO, clientIp);
		if (resp == null){
			return new ResponseEntity<>(HttpStatus.GATEWAY_TIMEOUT);
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
}
