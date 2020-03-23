package com.bharatpe.lending.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.bharatpe.lending.dto.ResponseDTO;

@RestController("lending/liquiloan/")
public class LiquiloanController {
	
	
	@RequestMapping(value = "/getStatus", method =RequestMethod.POST)
	public ResponseEntity<ResponseDTO> checkLoanStatus(){
		return new ResponseEntity<>(new ResponseDTO(true,null,null),HttpStatus.OK);
	}
}
