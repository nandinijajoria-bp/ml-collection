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
import com.bharatpe.lending.service.UploadDocumentService;

@RestController
@RequestMapping("lending")
public class UploadDocumentController {
	Logger logger = LoggerFactory.getLogger(UploadDocumentController.class);

	@Autowired
	UploadDocumentService uploadDocumentService;
	
	@RequestMapping(value="/uploadDocument", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Map<String, Object> uploadDocument(HttpServletRequest request, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
		logger.info("UploadDocument request : {}",commonAPIRequest);
		
		Map<String, Object> resp = uploadDocumentService.runService(request, response, commonAPIRequest);
		
		logger.info("UploadDocument response : {}", resp);
		return resp;
	}
}
