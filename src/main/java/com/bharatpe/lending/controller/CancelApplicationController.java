package com.bharatpe.lending.controller;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.service.CancelApplicationService;

@RestController
@RequestMapping("lending")
public class CancelApplicationController {
	Logger logger = LoggerFactory.getLogger(CancelApplicationController.class);
	
	@Autowired
	CancelApplicationService cancelApplicationService;
	
	@RequestMapping(value="/cancelApplication", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Object cancelApplication(@RequestAttribute Merchant merchant, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
		logger.info("cancelApplication request : {}",commonAPIRequest);
		
		Object resp = cancelApplicationService.cancleApplication(merchant, response, commonAPIRequest);
		
		logger.info("cancelApplication response : {}", resp);
		return resp;
	}
}
