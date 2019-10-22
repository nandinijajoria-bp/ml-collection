package com.bharatpe.lending.controller;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.bharatpe.lending.service.NotifyEligibleService;

@RestController
@RequestMapping("lending")
public class NotifyEligibleController {
	Logger logger = LoggerFactory.getLogger(NotifyEligibleController.class);

	@Autowired
	NotifyEligibleService notifyEligibleService;
	
	@RequestMapping(value="/notifyEligible", method = RequestMethod.GET, produces="application/json")
	public Map<String, Boolean> notifyEligible(HttpServletRequest request, HttpServletResponse response) {
		logger.info("notifyEligible request : {}",request);
		
		Map<String, Boolean> resp = notifyEligibleService.runService(request, response);
		
		logger.info("notifyEligible response : {}", response);
		return resp;
	}

}