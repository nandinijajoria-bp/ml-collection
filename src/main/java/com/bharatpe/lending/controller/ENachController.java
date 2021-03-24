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
import org.springframework.beans.factory.annotation.Value;
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

	@RequestMapping(value="/initiate", method = RequestMethod.GET, consumes="application/json", produces="application/json")
	public ResponseEntity<ENachIntitiationResponseDTO> initiateEnach(@RequestAttribute Merchant merchant, @RequestHeader("token") String token, @RequestParam(name = "provider", required = false) String provider, @RequestParam(name = "app_version", required = false) String appVersion) {
		ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
		responseDTO.setResponse(false);
		try {
			return new ResponseEntity<>(eNachService.eNachInitiate(merchant, token, provider), HttpStatus.OK);
		}
		catch (Exception e) {
			logger.error("Exception while initiating enach", e);
			responseDTO.setMessage("Something went wrong");
			return new ResponseEntity<>(responseDTO, HttpStatus.OK);
		}
	}

	@RequestMapping(value="/submit", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public ResponseEntity<ENachIntitiationResponseDTO> submit(@RequestAttribute Merchant merchant, @RequestBody ENachSubmitRequestDTO body, @RequestHeader("token") String token) {
		return new ResponseEntity<>(eNachService.submitEnach(merchant, body, token), HttpStatus.OK);
	}

	@RequestMapping(value="/skip",method = RequestMethod.GET, consumes="application/json", produces="application/json")
	public ResponseEntity<ResponseDTO> skipEnach(@RequestAttribute Merchant merchant){
		return new ResponseEntity<>(eNachService.setEnachSkipStatus(merchant), HttpStatus.OK);
	}

	@RequestMapping(value="/cancel",method = RequestMethod.PUT)
	public ResponseEntity<CommonResponse> cancelEnach(@RequestAttribute Merchant merchant){
		logger.info("Cancel enach request for merchant:{}", merchant.getId());
		return new ResponseEntity<>(eNachService.cancelEnach(merchant), HttpStatus.OK);
	}
}
