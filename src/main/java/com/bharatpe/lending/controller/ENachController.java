package com.bharatpe.lending.controller;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.service.ENachService;
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
	ENachService eNachService;

	@RequestMapping(value="/initiate", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public ResponseEntity<ENachIntitiationResponseDTO> initiateENach(@RequestAttribute Merchant merchant, @RequestAttribute String clientIp, @RequestBody(required = false) RequestDTO<String> requestDTO) {
		logger.info("E-Nach Init request : {}", requestDTO);
		ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();

		responseDTO = eNachService.eNachInitiate(merchant);

		return new ResponseEntity<>(responseDTO, HttpStatus.OK);
	}

	@RequestMapping(value="/submit", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Object submit(@RequestAttribute Merchant merchant, HttpServletResponse response, @RequestBody ENachSubmitRequestDTO body) {
		logger.info("Enach Submit request : {}",body);

		Boolean status = eNachService.submitEnach(merchant, body);

		return new ResponseEntity<>(status, HttpStatus.OK);	}
}
