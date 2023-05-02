package com.bharatpe.lending.controller;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.service.ENachService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("enach")
public class ENachController {

	Logger logger = LoggerFactory.getLogger(ENachController.class);

	@Autowired
	ENachService eNachService;

	ExecutorService executorService = Executors.newFixedThreadPool(10);

	@RequestMapping(value="/initiate", method = RequestMethod.GET, consumes="application/json", produces="application/json")
	public ResponseEntity<ENachIntitiationResponseDTO> initiateEnach(@RequestAttribute BasicDetailsDto merchant,
																	 @RequestHeader("token") String token,
																	 @RequestParam(name = "provider", required = false) String provider,
																	 @RequestParam(name = "app_version", required = false) String appVersion)
	{
		ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
		responseDTO.setResponse(false);
		try {
			return new ResponseEntity<>(eNachService.eNachInitiate(merchant, token, provider), HttpStatus.OK);
		}
		catch (Exception e) {
			logger.error("Exception while initiating enach", e);
			responseDTO.setMessage("Something went wrong");
			return new ResponseEntity<>(responseDTO, HttpStatus.OK);
		}
	}

	@RequestMapping(value="/submit", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public ResponseEntity<ENachIntitiationResponseDTO> submit(@RequestAttribute BasicDetailsDto merchant, @RequestBody ENachSubmitRequestDTO body, @RequestHeader("token") String token) {
		return new ResponseEntity<>(eNachService.submitEnach(merchant, body, token), HttpStatus.OK);
	}

	@RequestMapping(value="/skip",method = RequestMethod.GET, consumes="application/json", produces="application/json")
	public ResponseEntity<ResponseDTO> skipEnach(@RequestAttribute BasicDetailsDto merchant){
		return new ResponseEntity<>(eNachService.setEnachSkipStatus(merchant), HttpStatus.OK);
	}

	@RequestMapping(value="/cancel",method = RequestMethod.PUT)
	public ResponseEntity<CommonResponse> cancelEnach(@RequestAttribute BasicDetailsDto merchant){
		logger.info("Cancel enach request for merchant:{}", merchant.getId());
		return new ResponseEntity<>(eNachService.cancelEnach(merchant), HttpStatus.OK);
	}

	@RequestMapping(value="/cancelEnach",method = RequestMethod.PUT)
	public ResponseEntity<CommonResponse> cancelEnach(@RequestParam Long applicationId, @RequestParam Long merchantId){
		if (Objects.isNull(applicationId) || Objects.isNull(merchantId))
			return ResponseEntity.badRequest().body(new CommonResponse(false, "ApplicationId or merchantId missing"));
		logger.info("Cancel enach request for merchant:{}", merchantId);
		return new ResponseEntity<>(eNachService.cancelEnach(merchantId, applicationId), HttpStatus.OK);
	}

	@RequestMapping(value="/bulkNach",method = RequestMethod.POST)
	public ResponseEntity<String> bulkNach(@RequestBody EnachUploadRequestDTO enachUploadRequestDTO) {
		logger.info("Uploading Bulk Nach File : {}",enachUploadRequestDTO.getFileId());
		executorService.execute(()->eNachService.uploadBulkEnach(enachUploadRequestDTO));
		return new ResponseEntity<>("Bulk Nach Upload Successfully!",HttpStatus.OK);
	}
}
