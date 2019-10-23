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
import com.bharatpe.lending.service.LendingUploadService;

@RestController
@RequestMapping("lending")
public class LendingUploadController {
	Logger logger = LoggerFactory.getLogger(LendingUploadController.class);
	
	@Autowired
	LendingUploadService lendingUplaodService;
	
	@RequestMapping(value="/lendingUpload", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Object lendingUpload(@RequestAttribute Merchant merchant, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
		logger.info("lendingUpload request : {}",commonAPIRequest);
		
		Object resp = lendingUplaodService.uploadLoanDetails(merchant, response, commonAPIRequest);
		
		logger.info("lendingUpload response : {}", resp);
		return resp;
	}
}
