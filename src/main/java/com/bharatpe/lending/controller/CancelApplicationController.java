package com.bharatpe.lending.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.service.CancelApplicationService;

@RestController
@RequestMapping("lending")
public class CancelApplicationController {
	Logger logger = LoggerFactory.getLogger(CancelApplicationController.class);
	
	@Autowired
	CancelApplicationService cancelApplicationService;
	
	@RequestMapping(value="/cancelApplication", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Map<String, Boolean> cancelApplication(HttpServletRequest request, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
		Instant start = Instant.now();
		logger.info("cancelApplication request : {}",commonAPIRequest);
		
		Map<String, Boolean> resp = cancelApplicationService.runService(request, response, commonAPIRequest);
		
		logger.info("cancelApplication response : {}", resp);
		Instant end = Instant.now();
		logger.info("Time Taken by cancelApplication API : {} miliseconds", Duration.between(start, end).toMillis());
		return resp;
	}
}
