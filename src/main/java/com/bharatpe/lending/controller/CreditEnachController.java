package com.bharatpe.lending.controller;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bharatpe.lending.dto.ENachIntitiationResponseDTO;
import com.bharatpe.lending.dto.ENachSubmitRequestDTO;
import com.bharatpe.lending.dto.ResponseDTO;

@RestController
@RequestMapping("credit_line/enach")
public class CreditEnachController {


	Logger logger = LoggerFactory.getLogger(ENachController.class);

//	@Autowired
//	CreditENachService creditENachService;
	
	@Value("${enach.provider}")
	private String enachServiceToUse;

//	@RequestMapping(value="/initiate", method = RequestMethod.GET, consumes="application/json", produces="application/json")
//	public ResponseEntity<ENachIntitiationResponseDTO> initiateEnach(@RequestAttribute BasicDetailsDto merchant, @RequestParam(name = "app_version", required = false) String appVersion) {
//		ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
//		responseDTO.setResponse(false);
//		try {
////			if (merchant.getId().equals(4340760L)) {
////				return new ResponseEntity<>(creditENachService.enachInititateForDigio(merchant), HttpStatus.OK);
////			}
//			if(enachServiceToUse==null || (!enachServiceToUse.equals("digio") && !enachServiceToUse.equals("techprocess"))){
//				responseDTO.setMessage("Incorrect Enach service provider mentioned");
//				return new ResponseEntity<>(responseDTO, HttpStatus.OK);
//			}
//			else if(enachServiceToUse.equals("techprocess")) {
//				return new ResponseEntity<>(creditENachService.eNachInitiate(merchant, appVersion), HttpStatus.OK);
//			}
//			else {
//				return new ResponseEntity<>(creditENachService.enachInititateForDigio(merchant), HttpStatus.OK);
//			}
//		 }
//		catch (Exception e) {
//			logger.error("Exception while initiating enach", e);
//			responseDTO.setMessage("Something went wrong");
//			return new ResponseEntity<>(responseDTO, HttpStatus.OK);
//		}
//	}

//	@RequestMapping(value="/submit", method = RequestMethod.POST, consumes="application/json", produces="application/json")
//	public ResponseEntity<ENachIntitiationResponseDTO> submit(@RequestAttribute BasicDetailsDto merchant, @RequestBody ENachSubmitRequestDTO body) {
////		logger.info("Enach Submit request : {}", body);
////		if (merchant.getId().equals(4340760L)) {
////			return new ResponseEntity<>(creditENachService.submitEnachForDigio(merchant, body), HttpStatus.OK);
////		}
//		if(enachServiceToUse.equals("techprocess")) {
//			return new ResponseEntity<>(creditENachService.submitEnach(merchant, body), HttpStatus.OK);
//		}
//		else if(enachServiceToUse.equals("digio")){
//			return new ResponseEntity<>(creditENachService.submitEnachForDigio(merchant, body), HttpStatus.OK);
//		}
//		else {
//			logger.error("Mentioned wrong enach service provider");
//			ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
//			responseDTO.setResponse(false);
//			responseDTO.setMessage("Wrong enach serive provider");
//			return new ResponseEntity<>(responseDTO, HttpStatus.OK);
//		}
//	}
//
//	@RequestMapping(value="/skip",method = RequestMethod.GET, consumes="application/json", produces="application/json")
//	public ResponseEntity<ResponseDTO> skipEnach(@RequestAttribute BasicDetailsDto merchant){
//		return new ResponseEntity<>(creditENachService.setEnachSkipStatus(merchant), HttpStatus.OK);
//	}

}

