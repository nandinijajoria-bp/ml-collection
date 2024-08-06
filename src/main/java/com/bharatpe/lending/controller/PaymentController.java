package com.bharatpe.lending.controller;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.bharatpe.lending.service.PaymentCallbackRequestDTO;
import com.bharatpe.lending.service.PaymentService;

@RestController
@RequestMapping("lending/payment/**")
public class PaymentController {
	
	Logger logger = LoggerFactory.getLogger(PaymentController.class);
	
	@Autowired
	PaymentService paymentService;

    @RequestMapping(value="/details", method = RequestMethod.GET, produces="application/json")
    public ResponseEntity<PaymentDetailsResponseDTO> getPaymentDetails(@RequestAttribute BasicDetailsDto merchant,
                                                                       @RequestParam(required = false, defaultValue = "true") Boolean showForeClosureDetails) {
    	return new ResponseEntity<>(paymentService.getPaymentDetails(merchant, showForeClosureDetails), HttpStatus.OK);
    }
    
    @RequestMapping(value="/initiate", method = RequestMethod.POST,consumes = "application/json", produces="application/json")
    public ResponseEntity<InitiatePaymentResponseDTO> initiatePayment(@RequestHeader("token") String token, @RequestAttribute BasicDetailsDto merchant, @RequestBody RequestDTO<InitiatePaymentRequestDTO> requestDTO) {
    	return new ResponseEntity<>(paymentService.initiatePayment(merchant, requestDTO, token), HttpStatus.OK);
    }

    @RequestMapping(value="/initiate/v2", method = RequestMethod.POST,consumes = "application/json", produces="application/json")
    public ResponseEntity<InitiatePaymentResponseDTO> initiatePaymentV2(@RequestAttribute BasicDetailsDto merchant, @RequestBody RequestDTO<InitiatePaymentRequestDTO> requestDTO) {
        return new ResponseEntity<>(paymentService.initiatePaymentV2(merchant, requestDTO), HttpStatus.OK);
    }

    //for nach
    @RequestMapping(value="/callback", method = RequestMethod.POST,consumes = "application/json", produces="application/json")
    public ResponseEntity<String> callback(@RequestBody PaymentCallbackRequestDTO requestDTO) {
    	return new ResponseEntity<>(paymentService.handleCallback(requestDTO), HttpStatus.OK);
    }

    //normal txn
    @RequestMapping(value="/callback/v2", method = RequestMethod.POST,consumes = "application/json", produces="application/json")
    public ResponseEntity<String> callbackV2(@RequestBody PgPaymentCallbackDTO requestDTO) {
        return new ResponseEntity<>(paymentService.handlePgCallback(requestDTO), HttpStatus.OK);
    }

    @RequestMapping(value="/status", method = RequestMethod.GET, produces="application/json")
    public ResponseEntity<PaymentStatusResponseDTO> getPaymentStatus(@RequestAttribute BasicDetailsDto merchant, @RequestParam String orderId) {
        PaymentStatusResponseDTO responseDTO = paymentService.getStatus(orderId, merchant);
        logger.info("Response for status check request for orderId:{} and merchant:{} is {}", orderId, merchant.getId(), responseDTO);
        return new ResponseEntity<>(responseDTO, HttpStatus.OK);
    }

    @RequestMapping(value="/status/v2", method = RequestMethod.GET, produces="application/json")
    public ResponseEntity<PaymentStatusResponseDTO> getPaymentStatusV2(@RequestAttribute BasicDetailsDto merchant, @RequestParam String orderId) {
        PaymentStatusResponseDTO responseDTO = paymentService.getStatusV2(orderId, merchant);
        logger.info("Response for status check request for orderId:{} and merchant:{} is {}", orderId, merchant.getId(), responseDTO);
        return new ResponseEntity<>(responseDTO, HttpStatus.OK);
    }

    @RequestMapping(value="/modes", method = RequestMethod.POST, produces="application/json")
    public ResponseEntity<ResponseDTO> paymentModes(@RequestHeader("token") String token, @RequestAttribute BasicDetailsDto merchant, @RequestBody RequestDTO<CreditSpendRequestDTO> requestDTO) {
        logger.info("paymentModes request : {} for merchant:{}", requestDTO, merchant.getId());
        try {
            return new ResponseEntity<>(paymentService.getPaymentModes(requestDTO, token), HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception---", e);
            return new ResponseEntity<>(new ResponseDTO(false, "Something went wrong"), HttpStatus.OK);
        }
    }

    @RequestMapping(value="/resendOTP", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public ResponseEntity<ResponseDTO> resendOTP(@RequestHeader("token") String token, @RequestAttribute BasicDetailsDto merchant, @RequestBody RequestDTO<PaymentResendOTP> requestDTO) {
        logger.info("payment resend otp request for merchant {} : {}", merchant.getId(), requestDTO);
        try {
            return new ResponseEntity<>(paymentService.resendOTP(requestDTO, merchant, token), HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception---", e);
            return new ResponseEntity<>(new ResponseDTO(false, "Something went wrong"), HttpStatus.OK);
        }
    }

    @RequestMapping(value="/verify", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public ResponseEntity<ResponseDTO> verifyPayment(@RequestHeader("token") String token, @RequestAttribute BasicDetailsDto merchant, @RequestBody RequestDTO<PaymentResendOTP> requestDTO) {
        logger.info("payment verify request for merchant {} : {}", merchant.getId(), requestDTO);
        try {
            return new ResponseEntity<>(paymentService.verifyPayment(requestDTO, merchant, token), HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception---", e);
            return new ResponseEntity<>(new ResponseDTO(false, "Something went wrong"), HttpStatus.OK);
        }
    }

    @RequestMapping(value="/status/v3", method = RequestMethod.GET, produces="application/json")
    public ResponseEntity<PaymentStatusV3ResponseDTO> getPaymentStatusV3(@RequestAttribute BasicDetailsDto merchant, @RequestParam String orderId) {
        PaymentStatusV3ResponseDTO responseDTO = paymentService.getStatusV3(orderId, merchant);
        logger.info("Response for status check request for orderId:{} and merchant:{} is {}", orderId, merchant.getId(), responseDTO);
        return new ResponseEntity<>(responseDTO, HttpStatus.OK);
    }

    @RequestMapping(value = "/loan_settlement", method = RequestMethod.POST, produces="application/json")
    public ResponseDTO applyWaiver(@RequestBody LoanSettlementRequestDTO requestDTO) {
        logger.info("Request received for apply waiver {} for loan_id : {}", requestDTO.getWaiverType(), requestDTO.getLoanId());
        return paymentService.applyWaiver(requestDTO.getLoanId(), requestDTO.getMerchantId(), requestDTO.getWaiverType(), requestDTO.getCrmUserId());
    }

    @RequestMapping(value = "/refund", method = RequestMethod.GET, produces="application/json")
    public LoanRefundsResponseDTO getRefunds(@RequestParam Long loanId) {
        logger.info("Request received for refund details for loan id: {}", loanId);
        return paymentService.getRefunds(loanId);
    }

    @RequestMapping(value = "/ledger_entry", method = RequestMethod.POST, produces="application/json")
    public ResponseDTO applyWaiver(@RequestBody LedgerEntryDTO requestDTO) {
        logger.info("Request received for add entry in ledger: {}", requestDTO);
        return paymentService.createEntryInLedger(requestDTO);
    }

}