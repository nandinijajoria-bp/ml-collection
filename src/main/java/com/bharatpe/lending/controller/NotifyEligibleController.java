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
import com.bharatpe.lending.service.NotifyEligibleService;

@RestController
@RequestMapping("lending")
public class NotifyEligibleController {
	Logger logger = LoggerFactory.getLogger(NotifyEligibleController.class);

	@Autowired
	NotifyEligibleService notifyEligibleService;
	
	@RequestMapping(value="/notifyEligible", method = RequestMethod.GET, produces="application/json")
	public Object notifyEligible(@RequestAttribute Merchant merchant, HttpServletResponse response) {
		
		Object resp = notifyEligibleService.notifyEligible(merchant, response);
		
		logger.info("notifyEligible response : {}", response);
		return resp;
	}

}