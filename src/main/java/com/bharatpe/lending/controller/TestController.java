package com.bharatpe.lending.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("test")
public class TestController {
	
	private Logger logger = LoggerFactory.getLogger(TestController.class);

	@RequestMapping("/test")
	public String test(){
		logger.info("In Test Controller, Params {}, {}, {}", 1, 2, 3);
		return "Success!!";
	}
}
