package com.bharatpe.lending.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.dto.InitiatePaymentRequestDTO;
import com.bharatpe.lending.dto.InitiatePaymentResponseDTO;
import com.bharatpe.lending.dto.PaymentDetailsResponseDTO;
import com.bharatpe.lending.dto.RequestDTO;
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
    public ResponseEntity<InitiatePaymentResponseDTO> initiatePayment(@RequestAttribute Merchant merchant, @RequestBody RequestDTO<InitiatePaymentRequestDTO> requestDTO) {
    	return new ResponseEntity<>(paymentService.initiatePayment(merchant, requestDTO), HttpStatus.OK);
    }
    
    @RequestMapping(value="/callback", method = RequestMethod.POST,consumes = "application/json", produces="application/json")
    public ResponseEntity<String> callback(@RequestBody PaymentCallbackRequestDTO requestDTO) {
    	return new ResponseEntity<>(paymentService.handleCallback(requestDTO), HttpStatus.OK);
    }

}