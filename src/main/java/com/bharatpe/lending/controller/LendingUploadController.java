package com.bharatpe.lending.controller;

import javax.servlet.http.HttpServletResponse;

import com.bharatpe.lending.dto.LendingApplicationRequestDTO;
import com.bharatpe.lending.dto.LendingApplicationResponse;
import com.bharatpe.lending.dto.RequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.service.LendingApplicationService;

@RestController
@RequestMapping("lending")
public class LendingUploadController {
	Logger logger = LoggerFactory.getLogger(LendingUploadController.class);
	
	@Autowired
	LendingApplicationService lendingApplicationService;
	
	@RequestMapping(value="/createApplication", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public LendingApplicationResponse lendingUpload(@RequestAttribute Merchant merchant, HttpServletResponse response, @RequestBody RequestDTO<LendingApplicationRequestDTO> requestDTO) {
		logger.info("Create Application request : {}",requestDTO);
		LendingApplicationResponse resp = lendingApplicationService.createApplication(merchant, response, requestDTO);
		logger.info("Create Application response : {}", resp);
		return resp;
	}
}
