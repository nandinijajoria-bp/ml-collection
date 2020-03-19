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
	
	@Value("${enach.provider}")
	private String enachServiceToUse;

	@RequestMapping(value="/initiate", method = RequestMethod.GET, consumes="application/json", produces="application/json")
	public ResponseEntity<ENachIntitiationResponseDTO> initiateEnach(@RequestAttribute Merchant merchant) {
		ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
		responseDTO.setResponse(false);
		try {
			if(enachServiceToUse==null || (!enachServiceToUse.equals("digio") && !enachServiceToUse.equals("techprocess"))){
				responseDTO.setMessage("Incorrect Enach service provider mentioned");
				return new ResponseEntity<>(responseDTO, HttpStatus.OK);
			}
			else if(enachServiceToUse.equals("techprocess")) {
				return new ResponseEntity<>(eNachService.eNachInitiate(merchant), HttpStatus.OK);
			} 
			else {
				return new ResponseEntity<>(eNachService.enachInititateForDigio(merchant), HttpStatus.OK);
			}
		 }
		catch (Exception e) {
			logger.error("Exception while initiating enach", e);
			responseDTO.setMessage("Something went wrong");
			return new ResponseEntity<>(responseDTO, HttpStatus.OK);
		}
	}

	@RequestMapping(value="/submit", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public ResponseEntity<ENachIntitiationResponseDTO> submit(@RequestAttribute Merchant merchant, @RequestBody ENachSubmitRequestDTO body) {
		logger.info("Enach Submit request : {}", body);
		return new ResponseEntity<>(eNachService.submitEnach(merchant, body), HttpStatus.OK);
	}

	@RequestMapping(value="/skip",method = RequestMethod.GET, consumes="application/json", produces="application/json")
	public ResponseEntity<ResponseDTO> skipEnach(@RequestAttribute Merchant merchant){
		return new ResponseEntity<>(eNachService.setEnachSkipStatus(merchant), HttpStatus.OK);
	}
}
