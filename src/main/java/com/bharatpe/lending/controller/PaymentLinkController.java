package com.bharatpe.lending.controller;


import com.bharatpe.lending.dto.InitiatePaymentRequestDTO;
import com.bharatpe.lending.dto.InitiatePaymentResponseDTO;
import com.bharatpe.lending.dto.PaymentDetailsResponseDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("lending/payment_link")
@Slf4j
public class PaymentLinkController {

    @Autowired
    private PaymentService paymentService;
    @GetMapping(value = "/v1/payment_details")
    public ResponseEntity<PaymentDetailsResponseDTO> getPaymentLinkDetails(
            @RequestAttribute(name = "merchant_id") Long merchantId,
            @RequestAttribute(name = "external_loan_id") String externalLoanId)
    {
        return new ResponseEntity<>(paymentService.getPaymentDetails(merchantId,externalLoanId), HttpStatus.OK);

    }

    @PostMapping(value = "/v1/payment_initiate")
    public ResponseEntity<InitiatePaymentResponseDTO> initiatePaymentV1(
            @RequestAttribute(name = "merchant_id") Long merchantId,
            @RequestAttribute(name = "external_loan_id") String externalLoanId,@RequestBody RequestDTO<InitiatePaymentRequestDTO> requestDTO)
    {
        return new ResponseEntity<>(paymentService.initiatePaymentThroughLink(merchantId,externalLoanId,requestDTO),HttpStatus.OK);

    }
}
