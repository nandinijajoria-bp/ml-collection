package com.bharatpe.lending.controller;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.service.LoanDetailsService;

@RestController
@RequestMapping("lending")
public class LoanDetailsController {
	Logger logger = LoggerFactory.getLogger(LoanDetailsController.class);
	
	@Autowired
	LoanDetailsService loanDetailsService;
	
	@RequestMapping(value="/loanDetails", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Map<String, Object> loanDetails(HttpServletRequest request, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
		logger.info("loanDetails request : {}",request);
		
		Map<String, Object> resp = loanDetailsService.runService(request, response, commonAPIRequest);
		
		logger.info("loanDetails response : {}", resp);
		return resp;
	}

}
