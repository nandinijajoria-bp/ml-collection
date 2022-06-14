package com.bharatpe.lending.controller;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.service.CreditLineBPBService;
import com.bharatpe.lending.service.CreditLineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("lending/credit_line/bpb")
public class CreditLineBPBController {

    Logger logger = LoggerFactory.getLogger(CreditLineBPBController.class);

    @Autowired
    CreditLineBPBService creditLineBPBService;

    @Autowired
    CreditLineService creditLineService;

    @RequestMapping(value="/check_balance", method = RequestMethod.GET, consumes="application/json", produces="application/json")
    public ResponseEntity<CheckBalanceResponseDTO> checkBalance(@RequestAttribute BasicDetailsDto merchant, @RequestHeader(name = "client") String client) {
        try {
            return new ResponseEntity<>(creditLineBPBService.getBalance(merchant, client), HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception while checking credit line balance", e);
            return new ResponseEntity<>(new CheckBalanceResponseDTO(false, "Something went wrong!!!"), HttpStatus.OK);
        }
    }

    @RequestMapping(value="/create_txn", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public ResponseEntity<CreditSpendResponseDTO> spend(@RequestAttribute BasicDetailsDto merchant, @RequestBody CreateTxnRequestDTO requestDTO) {
        logger.info("Credit line create txn request: {}", requestDTO);
        try {
            return new ResponseEntity<>(creditLineBPBService.createTxn(merchant.getId(), requestDTO), HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception---", e);
            return new ResponseEntity<>(new CreditSpendResponseDTO(false, "Something went wrong"), HttpStatus.OK);
        }
    }

    @RequestMapping(value="/deduct", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public ResponseEntity<CreditSpendVerifyResponseDTO> deduct(@RequestAttribute BasicDetailsDto merchant, @RequestBody CreditDeductRequestDTO requestDTO) {
        logger.info("Credit line deduct txn request: {}", requestDTO);
        try {
            return new ResponseEntity<>(creditLineBPBService.deductCL(merchant, requestDTO), HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception---", e);
            return new ResponseEntity<>(new CreditSpendVerifyResponseDTO(false, "Something went wrong"), HttpStatus.OK);
        }
    }

    @RequestMapping(value="/check_status", method = RequestMethod.GET, consumes="application/json", produces="application/json")
    public ResponseEntity<CreditSpendVerifyResponseDTO> checkStatus(@RequestParam Long orderId) {
        logger.info("Credit line check status request: {}", orderId);
        try {
            return new ResponseEntity<>(creditLineBPBService.checkStatus(orderId), HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception---", e);
            return new ResponseEntity<>(new CreditSpendVerifyResponseDTO(false, "Something went wrong"), HttpStatus.OK);
        }
    }

    @RequestMapping(value="/refund", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public ResponseEntity<CreditSpendVerifyResponseDTO> refund(@RequestBody CreditRefundRequestDTO requestDTO) {
        logger.info("Credit line refund txn request: {}", requestDTO);
        try {
            return new ResponseEntity<>(creditLineBPBService.refund(requestDTO), HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception---", e);
            return new ResponseEntity<>(new CreditSpendVerifyResponseDTO(false, "Something went wrong"), HttpStatus.OK);
        }
    }
}
