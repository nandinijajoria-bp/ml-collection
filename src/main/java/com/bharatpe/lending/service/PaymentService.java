package com.bharatpe.lending.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.dto.InitiatePaymentRequestDTO;
import com.bharatpe.lending.dto.InitiatePaymentResponseDTO;
import com.bharatpe.lending.dto.PaymentDetailsResponseDTO;

@Service
public class PaymentService {

	Logger logger = LoggerFactory.getLogger(PaymentService.class);
	
	
	public PaymentDetailsResponseDTO getPaymentDetails(Merchant merchant) {
		logger.info("Received payment details request for merchant id {}", merchant.getId());
		try {
			return new PaymentDetailsResponseDTO();
		} catch(Exception ex) {
			logger.error("Execption while fetching payment details for merchant id {}, Exception is {}", merchant.getId(), ex);
		}
		return null;
	}
	
	public InitiatePaymentResponseDTO initiatePayment(Merchant merchant, InitiatePaymentRequestDTO request) {
		logger.info("Received initiate payment request  for merchant {} : {}", merchant.getId(), request);
		try {
			return new InitiatePaymentResponseDTO();
		} catch(Exception ex) {
			logger.error("Execption while initiating payment for merchant id {}, Exception is {}", merchant.getId(), ex);
		}
		return null;
	}
	
	public String handleCallback(PaymentCallbackRequestDTO request) {
		logger.info("Received payment callback request for merchant {} : {}", request.getMerchantId(), request);
		try {
			
		} catch(Exception ex) {
			logger.error("Execption while initiating payment for merchant id {}, Exception is {}", request.getMerchantId(), ex);
		}
		
		return null;
	}
}
