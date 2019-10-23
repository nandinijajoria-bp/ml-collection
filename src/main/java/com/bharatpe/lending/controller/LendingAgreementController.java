package com.bharatpe.lending.controller;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.service.LendingAgreementService;

@RestController
@RequestMapping("lending")
public class LendingAgreementController {
	Logger logger = LoggerFactory.getLogger(LendingAgreementController.class);
	
	@Autowired
	LendingAgreementService lendingAgreementService;
	
	@RequestMapping(value="/lendingAgreement", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Object lendingAgreement(@RequestAttribute Merchant merchant, HttpServletResponse response) {
		
		Object resp = lendingAgreementService.fetchLendingAgreement(merchant, response);
		
		logger.info("LendingAgreement response : {}", resp);
		return resp;
	}

}
