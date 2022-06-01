package com.bharatpe.lending.controller;

 
import com.bharatpe.lending.service.merchant.dto.BasicDetailsDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.bharatpe.lending.dto.CreditLineKycResponseDto;
import com.bharatpe.lending.dto.EKycRequestDTO;
import com.bharatpe.lending.dto.EkycManualRequestDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.service.CreditLineKycService;
 



@RestController
@RequestMapping("lending/credit_line/ekyc")
public class EkycController {

	
	@Autowired
	CreditLineKycService creditLineKycService;
	
	@RequestMapping(value = "/initiate", method = RequestMethod.GET)
	public Object initiateEkyc(@RequestAttribute BasicDetailsDto merchant) {
	 
		return creditLineKycService.eKycInitiate(merchant);
		 
	}
	
	@RequestMapping(value = "/submit", method = RequestMethod.POST)
	public Object submitEkyc(@RequestAttribute BasicDetailsDto merchant,@RequestBody RequestDTO<EKycRequestDTO> requestDTO) {
	 
		return creditLineKycService.eKycSubmit(merchant,requestDTO);
		 
	}
	@RequestMapping(value = "/fetch_address", method = RequestMethod.GET)
	public CreditLineKycResponseDto fetchAddress(@RequestAttribute BasicDetailsDto merchant) {
		
		return creditLineKycService.fetchAddress(merchant);
		
	}
	
	
		@RequestMapping(value = "/verify_address", method = RequestMethod.POST)
	public Object verifyAddress(@RequestAttribute BasicDetailsDto merchant,@RequestBody RequestDTO<EkycManualRequestDTO> requestDTO) {
		
		return creditLineKycService.verifyAddress(merchant,requestDTO);
		 
	}
}
