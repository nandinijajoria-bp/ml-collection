package com.bharatpe.lending.controller;

import com.bharatpe.common.entities.LendingPaymentSchedule;
//import com.bharatpe.lending.common.dao.LendingTlDetailsDao;
import com.bharatpe.lending.common.dao.LiquiloansDirectDisbursalRawResponseDao;
import com.bharatpe.lending.common.entity.LendingTlDetails;
import com.bharatpe.lending.common.entity.LiquiloansDirectDisbursalRawResponse;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.service.LiquiloansService;

import java.util.Optional;

import static com.bharatpe.lending.enums.Lender.LIQUILOANS_NBFC;
import static com.bharatpe.lending.enums.Lender.LIQUILOANS_P2P;
import static com.bharatpe.lending.enums.Lender.LIQUILOANS_P2P_OF;

@RestController
@RequestMapping("lending/liquiloan/*")
public class LiquiloanController {

	Logger logger = LoggerFactory.getLogger(LiquiloanController.class);
	
	@Autowired
	LiquiloansService liquilaonService;

	@Autowired
	LiquiloansDirectDisbursalRawResponseDao liquiloansDirectDisbursalRawResponseDao;

	@Autowired
	LendingPaymentScheduleDao lendingPaymentScheduleDao;

//	@Autowired
//	LendingTlDetailsDao lendingTlDetailsDao;
	
	@RequestMapping(value = "approveLoan", method =RequestMethod.POST)
	public ResponseEntity<ResponseDTO> checkLoanStatus(@RequestBody LiquiloanCallbackRequestDTO callbackRequestDto){
		logger.info("Approve Loan request:{}", callbackRequestDto);
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

	@RequestMapping(value = "/callPayout", method = RequestMethod.GET)
	public void callLoanDetails(@RequestParam Long id) {
		liquilaonService.publishForDisbursal(id);
	}

//	@RequestMapping(value="create_lead",method=RequestMethod.GET)
//	public ResponseEntity<ResponseDTO> createLead(@RequestParam Long loanId){
//		Optional<LendingPaymentSchedule> lendingPaymentSchedule = lendingPaymentScheduleDao.findById(loanId);
//		if (!lendingPaymentSchedule.isPresent()) {
//			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
//		}
//		Optional<LendingTlDetails> lendingTlDetails = lendingTlDetailsDao.findById(lendingPaymentSchedule.get().getTlDetailsId());
//		if (!lendingTlDetails.isPresent() || lendingTlDetails.get().getNbfcId() != null) {
//			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
//		}
//		liquilaonService.createLead(lendingPaymentSchedule.get(), lendingTlDetails.get());
//		return new ResponseEntity<>(HttpStatus.OK);
//	}

	@RequestMapping(value="postPayout/callback",method=RequestMethod.POST)
	public ResponseEntity<PostPayoutResponseDto> postPayoutCallback(@RequestBody PostPayoutRequestDto postPayoutRequestDto){
		logger.info("postPayout callback request:{}", postPayoutRequestDto);
		return liquilaonService.populatePostPayoutSchedule(postPayoutRequestDto);
	}

	@RequestMapping(value="nbfc/postPayout/callback",method=RequestMethod.POST)
	public ResponseEntity<PostPayoutResponseDto> postPayoutCallbackForLiquiloansNBFC(@RequestBody PostPayoutRequestDto postPayoutRequestDto){
		logger.info("postPayout callback for LiquiloansNBFC request:{}", postPayoutRequestDto);
		postPayoutRequestDto.setLender(LIQUILOANS_NBFC.name());
		postPayoutRequestDto.setApplicationId(postPayoutRequestDto.getUrn());
		return liquilaonService.populatePostPayoutSchedule(postPayoutRequestDto);
	}

	@RequestMapping(value="p2p/postPayout/callback",method=RequestMethod.POST)
	public ResponseEntity<PostPayoutResponseDto> postPayoutCallbackForLiquiloansP2P(@RequestBody PostPayoutRequestDto postPayoutRequestDto){
		logger.info("postPayout callback for LiquiloansP2P request:{}", postPayoutRequestDto);
		postPayoutRequestDto.setLender(LIQUILOANS_P2P.name());
		postPayoutRequestDto.setApplicationId(postPayoutRequestDto.getUrn());
		return liquilaonService.populatePostPayoutSchedule(postPayoutRequestDto);
	}

	@RequestMapping(value="p2p_of/postPayout/callback",method=RequestMethod.POST)
	public ResponseEntity<PostPayoutResponseDto> postPayoutCallbackForLiquiloansP2P_OF(@RequestBody PostPayoutRequestDto postPayoutRequestDto){
		logger.info("postPayout callback for LIQUILOANS_P2P_OF request:{}", postPayoutRequestDto);
		postPayoutRequestDto.setLender(LIQUILOANS_P2P_OF.name());
		postPayoutRequestDto.setApplicationId(postPayoutRequestDto.getUrn());
		return liquilaonService.populatePostPayoutSchedule(postPayoutRequestDto);
	}
}
