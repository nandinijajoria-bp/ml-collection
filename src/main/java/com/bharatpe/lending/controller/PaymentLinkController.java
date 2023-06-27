package com.bharatpe.lending.controller;


import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dto.*;
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
            @RequestAttribute(name = "merchantId") Long merchantId,
            @RequestAttribute(name = "externalLoanId") String externalLoanId)
    {
        return new ResponseEntity<>(paymentService.getPaymentDetails(merchantId,externalLoanId), HttpStatus.OK);

    }

    @PostMapping(value = "/v1/payment_initiate")
    public ResponseEntity<InitiatePaymentResponseDTO> initiatePayment(
            @RequestAttribute(name = "merchantId") Long merchantId,
            @RequestAttribute(name = "externalLoanId") String externalLoanId,@RequestBody RequestDTO<InitiatePaymentRequestDTO> requestDTO)
    {
        return new ResponseEntity<>(paymentService.initiatePaymentThroughLink(merchantId,externalLoanId,requestDTO),HttpStatus.OK);

    }

    @RequestMapping(value="v1/status", method = RequestMethod.GET, produces="application/json")
    public ResponseEntity<PaymentStatusV3ResponseDTO> getPaymentStatus( @RequestAttribute(name = "merchantId") Long merchantId, @RequestParam String orderId) {
        return new ResponseEntity<>(paymentService.getPaymentStatusForPaymentLink(orderId,merchantId), HttpStatus.OK);
    }
}
