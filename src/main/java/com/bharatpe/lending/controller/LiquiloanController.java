package com.bharatpe.lending.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.LiquiloanCallbackRequestDTO;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.service.LiquiloansService;

@RestController
@RequestMapping("lending/liquiloan/*")
public class LiquiloanController {
	
	@Autowired
	LiquiloansService liquilaonService;
	
	
	Logger logger=LoggerFactory.getLogger(LiquiloanController.class);
	
	@RequestMapping(value = "getStatus", method =RequestMethod.POST)
	public ResponseEntity<ResponseDTO> checkLoanStatus(@RequestBody LiquiloanCallbackRequestDTO callbackRequestDto){
		//fetching lending application for given liquiloan_loan_id and bp_loan_id
		return new ResponseEntity<>(liquilaonService.checkLoanStatus(callbackRequestDto),HttpStatus.OK);
	}
}
