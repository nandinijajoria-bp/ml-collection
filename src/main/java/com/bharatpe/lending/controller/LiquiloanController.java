package com.bharatpe.lending.controller;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingTlDetailsDao;
import com.bharatpe.lending.common.dao.LiquiloansDirectDisbursalRawResponseDao;
import com.bharatpe.lending.common.entity.LendingTlDetails;
import com.bharatpe.lending.common.entity.LiquiloansDirectDisbursalRawResponse;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.LiquidatePostPayoutStatusUpdateRequestDTO;
import com.bharatpe.lending.dto.LiquiloanCallbackRequestDTO;
import com.bharatpe.lending.dto.LiquiloanSettlementRequestDTO;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.service.LiquiloansService;

import java.util.Optional;

@RestController
@RequestMapping("lending/liquiloan/*")
public class LiquiloanController {
	
	@Autowired
	LiquiloansService liquilaonService;

	@Autowired
	LiquiloansDirectDisbursalRawResponseDao liquiloansDirectDisbursalRawResponseDao;

	@Autowired
	LendingPaymentScheduleDao lendingPaymentScheduleDao;

	@Autowired
	LendingTlDetailsDao lendingTlDetailsDao;
	
	
	Logger logger=LoggerFactory.getLogger(LiquiloanController.class);
	
	@RequestMapping(value = "approveLoan", method =RequestMethod.POST)
	public ResponseEntity<ResponseDTO> checkLoanStatus(@RequestBody LiquiloanCallbackRequestDTO callbackRequestDto){
		//fetching lending application for given liquiloan_loan_id and bp_loan_id
		LiquiloansDirectDisbursalRawResponse liquiloansDirectDisbursalRawResponse = new LiquiloansDirectDisbursalRawResponse();
		ResponseDTO responseDTO = liquilaonService.checkLoanStatus(callbackRequestDto, liquiloansDirectDisbursalRawResponse);
		liquiloansDirectDisbursalRawResponse.setResponse(responseDTO.toString());
		liquiloansDirectDisbursalRawResponse.setStatus(responseDTO.isSuccess() ? "SUCCESS" : "FAILED");
		liquiloansDirectDisbursalRawResponseDao.save(liquiloansDirectDisbursalRawResponse);
		return new ResponseEntity<>(responseDTO, HttpStatus.OK);
	}
	
	@RequestMapping(value="postPayoutStatusUpdate",method=RequestMethod.POST)
	public ResponseEntity<String> postPayoutStatusUpdate(@RequestBody LiquidatePostPayoutStatusUpdateRequestDTO postPayoutRequestDto){
		
		if(postPayoutRequestDto.getStatus().equalsIgnoreCase("SUCCESS")){
			return liquilaonService.populateLendingPaymentSchedule(postPayoutRequestDto);
		}
		return new ResponseEntity<>("Ok", HttpStatus.OK);
	}
	
	@RequestMapping(value="settlement",method=RequestMethod.POST)
	public ResponseEntity<ResponseDTO> populateSettlementDetails(@RequestBody LiquiloanSettlementRequestDTO settlementRequestDto){
		LiquiloansDirectDisbursalRawResponse liquiloansDirectDisbursalRawResponse = new LiquiloansDirectDisbursalRawResponse();
		ResponseDTO responseDTO = liquilaonService.populateSettlementDetails(settlementRequestDto,liquiloansDirectDisbursalRawResponse);
		liquiloansDirectDisbursalRawResponse.setResponse(responseDTO.toString());
		liquiloansDirectDisbursalRawResponse.setStatus(responseDTO.isSuccess() ? "SUCCESS" : "FAILED");
		liquiloansDirectDisbursalRawResponseDao.save(liquiloansDirectDisbursalRawResponse);
		return new ResponseEntity<>(responseDTO,HttpStatus.OK);
	}

	@RequestMapping(value="create_lead",method=RequestMethod.GET)
	public ResponseEntity<ResponseDTO> createLead(@RequestParam Long loanId){
		Optional<LendingPaymentSchedule> lendingPaymentSchedule = lendingPaymentScheduleDao.findById(loanId);
		if (!lendingPaymentSchedule.isPresent()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		Optional<LendingTlDetails> lendingTlDetails = lendingTlDetailsDao.findById(lendingPaymentSchedule.get().getTlDetailsId());
		if (!lendingTlDetails.isPresent() || lendingTlDetails.get().getNbfcId() != null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		liquilaonService.createLead(lendingPaymentSchedule.get(), lendingTlDetails.get());
		return new ResponseEntity<>(HttpStatus.OK);
	}
}
