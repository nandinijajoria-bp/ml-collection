package com.bharatpe.lending.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.dto.CreditLineKycResponseDto;
import com.bharatpe.lending.dto.EKycRequestDTO;
import com.bharatpe.lending.dto.EkycManualRequestDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.service.CreditLineKycService;
import com.bharatpe.lending.service.LendingEkycService;

@RestController
@RequestMapping("lending/ekyc")
public class EkycLendingContoller {
	@Autowired
	LendingEkycService lendingEkycService;
	
	@RequestMapping(value = "/initiate", method = RequestMethod.GET)
	public Object initiateEkyc(@RequestAttribute Merchant merchant) {
	 
		return lendingEkycService.eKycInitiate(merchant);
		 
	}
	
	@RequestMapping(value = "/submit", method = RequestMethod.POST)
	public Object submitEkyc(@RequestAttribute Merchant merchant,@RequestBody RequestDTO<EKycRequestDTO> requestDTO) {
	 
		return lendingEkycService.eKycSubmit(merchant,requestDTO);
		 
	}

}
