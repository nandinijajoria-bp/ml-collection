package com.bharatpe.lending.controller.panel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.bharatpe.lending.dto.CreditApplicationStatusUpdationRequestDto;
import com.bharatpe.lending.dto.ResponseDTO;

@RestController
@RequestMapping("lending/credit_line")
public class UpdateApplicationStatus {
	
//	@Autowired
//	CreditApplicationStatusChange creditApplicationStatusChange;
	
//	@RequestMapping(value = "/application_status_update",method = RequestMethod.POST)
//	public ResponseDTO updateApplicationStatus(@RequestBody CreditApplicationStatusUpdationRequestDto applicationStatus){
//
//		return creditApplicationStatusChange.changeApplicationStatus(applicationStatus);
//	}
}
