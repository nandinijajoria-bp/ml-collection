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
import com.bharatpe.lending.service.ImageURLService;

@RestController
@RequestMapping("lending")
public class ImageURLController {
	Logger logger = LoggerFactory.getLogger(ImageURLController.class);
	
	@Autowired
	ImageURLService imageURLService;
	
	@RequestMapping(value="/imageURL", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Object imageURL(@RequestAttribute Merchant merchant, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
		logger.info("ImageURLController request : {}",commonAPIRequest);
		
		Object resp = imageURLService.fetchImageUrl(merchant, response, commonAPIRequest);
		
		logger.info("ImageURLController response : {}", resp);
		return resp;
	}
}
