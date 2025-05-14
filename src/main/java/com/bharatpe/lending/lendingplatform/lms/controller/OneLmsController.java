package com.bharatpe.lending.lendingplatform.lms.controller;

import com.bharatpe.lending.lendingplatform.lms.service.PaymentAsynchronousService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/lms/v1")
@RequiredArgsConstructor
public class OneLmsController {

	private final PaymentAsynchronousService paymentAsynchronousService;

	@PostMapping("/payment")
	public ResponseEntity<String> postPaymentToLMS(@RequestParam(name = "terminalOrderId") String terminalOrderId) {
		log.info("Received payment posting request for terminalOrderId: {}", terminalOrderId);
		paymentAsynchronousService.postSettlementPaymentLMS(terminalOrderId);
		return ResponseEntity.ok("success");
	}
}
