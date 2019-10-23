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
import com.bharatpe.lending.service.UploadDocumentService;

@RestController
@RequestMapping("lending")
public class UploadDocumentController {
	Logger logger = LoggerFactory.getLogger(UploadDocumentController.class);

	@Autowired
	UploadDocumentService uploadDocumentService;
	
	@RequestMapping(value="/uploadDocument", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Object uploadDocument(@RequestAttribute Merchant merchant, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
		logger.info("UploadDocument request : {}",commonAPIRequest);
		
		Object resp = uploadDocumentService.runService(merchant, response, commonAPIRequest);
		
		logger.info("UploadDocument response : {}", resp);
		return resp;
	}
}
