package com.bharatpe.lending.controller;

import java.time.Duration;
import java.time.Instant;
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
import com.bharatpe.lending.service.CancelApplicationService;
import com.bharatpe.lending.service.SaveApplicationAddressService;

@RestController
@RequestMapping("lending/csPanel")
public class SaveApplicationAddressController {
		Logger logger = LoggerFactory.getLogger(SaveApplicationAddressController.class);
		
		@Autowired
		SaveApplicationAddressService saveApplicationAddressService;
		
		@RequestMapping(value="/saveApplicationAddress", method = RequestMethod.POST, consumes="application/json", produces="application/json")
		public Map<String, String> saveApplicationAddress(HttpServletRequest request, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
			Instant start = Instant.now();
			logger.info("saveApplicationAddress request : {}",commonAPIRequest);
			
			Map<String, String> resp = saveApplicationAddressService.runService(request, response, commonAPIRequest);
			
			logger.info("saveApplicationAddress response : {}", resp);
			Instant end = Instant.now();
			logger.info("Time Taken by saveApplicationAddress API : {} miliseconds", Duration.between(start, end).toMillis());
			return resp;
		}
}
