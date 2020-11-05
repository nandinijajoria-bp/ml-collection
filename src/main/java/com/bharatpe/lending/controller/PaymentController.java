package com.bharatpe.lending.controller;

import com.bharatpe.lending.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.service.PaymentCallbackRequestDTO;
import com.bharatpe.lending.service.PaymentService;

@RestController
@RequestMapping("lending/payment/**")
public class PaymentController {
	
	Logger logger = LoggerFactory.getLogger(PaymentController.class);
	
	@Autowired
	PaymentService paymentService;

    @RequestMapping(value="/details", method = RequestMethod.GET, produces="application/json")
    public ResponseEntity<PaymentDetailsResponseDTO> getPaymentDetails(@RequestAttribute Merchant merchant) {
    	return new ResponseEntity<>(paymentService.getPaymentDetails(merchant), HttpStatus.OK);
    }
    
    @RequestMapping(value="/initiate", method = RequestMethod.POST,consumes = "application/json", produces="application/json")
    public ResponseEntity<InitiatePaymentResponseDTO> initiatePayment(@RequestHeader("token") String token, @RequestAttribute Merchant merchant, @RequestBody RequestDTO<InitiatePaymentRequestDTO> requestDTO) {
    	return new ResponseEntity<>(paymentService.initiatePayment(merchant, requestDTO, token), HttpStatus.OK);
    }
    
    @RequestMapping(value="/callback", method = RequestMethod.POST,consumes = "application/json", produces="application/json")
    public ResponseEntity<String> callback(@RequestBody PaymentCallbackRequestDTO requestDTO) {
    	return new ResponseEntity<>(paymentService.handleCallback(requestDTO), HttpStatus.OK);
    }

    @RequestMapping(value="/status", method = RequestMethod.GET, produces="application/json")
    public ResponseEntity<PaymentStatusResponseDTO> getPaymentStatus(@RequestAttribute Merchant merchant, @RequestParam String orderId) {
        return new ResponseEntity<>(paymentService.getStatus(orderId, merchant), HttpStatus.OK);
    }

    @RequestMapping(value="/modes", method = RequestMethod.GET, produces="application/json")
    public ResponseEntity<ResponseDTO> paymentModes(@RequestHeader("token") String token, @RequestAttribute Merchant merchant, @RequestBody RequestDTO<CreditSpendRequestDTO> requestDTO) {
        logger.info("paymentModes request : {} for merchant:{}", requestDTO, merchant.getId());
        try {
            return new ResponseEntity<>(paymentService.getPaymentModes(requestDTO, token), HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception---", e);
            return new ResponseEntity<>(new ResponseDTO(false, "Something went wrong"), HttpStatus.OK);
        }
    }

    @RequestMapping(value="/resendOTP", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public ResponseEntity<ResponseDTO> resendOTP(@RequestHeader("token") String token, @RequestAttribute Merchant merchant, @RequestBody RequestDTO<PaymentResendOTP> requestDTO) {
        logger.info("payment resend otp request for merchant {} : {}", merchant.getId(), requestDTO);
        try {
            return new ResponseEntity<>(paymentService.resendOTP(requestDTO, merchant, token), HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception---", e);
            return new ResponseEntity<>(new ResponseDTO(false, "Something went wrong"), HttpStatus.OK);
        }
    }

    @RequestMapping(value="/verify", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public ResponseEntity<ResponseDTO> verifyPayment(@RequestHeader("token") String token, @RequestAttribute Merchant merchant, @RequestBody RequestDTO<PaymentResendOTP> requestDTO) {
        logger.info("payment verify request for merchant {} : {}", merchant.getId(), requestDTO);
        try {
            return new ResponseEntity<>(paymentService.verifyPayment(requestDTO, merchant, token), HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception---", e);
            return new ResponseEntity<>(new ResponseDTO(false, "Something went wrong"), HttpStatus.OK);
        }
    }

}