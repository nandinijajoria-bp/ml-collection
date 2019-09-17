package com.bharatpe.lending.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import com.bharatpe.lending.service.ImageURLService;

@RestController
@RequestMapping("lending")
public class ImageURLController {
	Logger logger = LoggerFactory.getLogger(ImageURLController.class);
	
	@Autowired
	ImageURLService imageURLService;
	
	@RequestMapping(value="/imageURL", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public List<Map<String, Object>> imageURL(HttpServletRequest request, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
		Instant start = Instant.now();
		logger.info("ImageURLController request : {}",commonAPIRequest);
		
		List<Map<String, Object>> resp = imageURLService.runService(request, response, commonAPIRequest);
		
		logger.info("ImageURLController response : {}", resp);
		Instant end = Instant.now();
		logger.info("Time Taken by imageURL API : {} miliseconds", Duration.between(start, end).toMillis());
		return resp;
	}
}
