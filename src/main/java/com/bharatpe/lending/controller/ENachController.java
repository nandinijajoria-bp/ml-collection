package com.bharatpe.lending.controller;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.dto.IneligibleRequestDTO;
import com.bharatpe.lending.dto.LoanDetailsResponseDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.service.ImageURLService;
import com.bharatpe.lending.service.LendingAgreementService;
import com.bharatpe.lending.service.LoanDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("enach")
public class ENachController {
	Logger logger = LoggerFactory.getLogger(ENachController.class);
	
	@Autowired
	LoanDetailsService loanDetailsService;

	@Autowired
	LendingAgreementService lendingAgreementService;

	@RequestMapping(value="/initiate", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public ResponseEntity<LoanDetailsResponseDTO> initiateENach(@RequestAttribute Merchant merchant, @RequestAttribute String clientIp, @RequestBody(required = false) RequestDTO<IneligibleRequestDTO> requestDTO) {
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

	@RequestMapping(value="/submit", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Object imageURL(@RequestAttribute Merchant merchant, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
		logger.info("ImageURLController request : {}",commonAPIRequest);

		return null;
	}
}
