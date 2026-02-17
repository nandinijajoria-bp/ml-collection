package com.bharatpe.lending.controller;


import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.service.AutoPayUPIService;
import com.bharatpe.lending.service.validator.AutoPayUPIServiceValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/auto-pay")
@Slf4j
public class AutoPayUPIController {

    @Autowired
    AutoPayUPIService autoPayUPIService;

    @Autowired
    AutoPayUPIServiceValidator autoPayUPIServiceValidator;

    @PostMapping(value = "/mandate/register")
    public UPIRegisterResponseDto registerAutoPayForMerchantForActiveLoan(
            @RequestAttribute BasicDetailsDto merchant,
            @RequestBody(required = true)
            RequestDTO<UPIRegisterRequestDto> requestDTO) {
        return autoPayUPIService.registerUPI(merchant, requestDTO);

    }


    @PostMapping(value = "/mandate/register/application")
    public UPIRegisterResponseDto registerAutoPayForMerchantForNewApplication(
      @RequestAttribute BasicDetailsDto merchant,
      @RequestBody RequestDTO<AutoUPIMandateRegisterRequestDto> requestDTO) {
        log.info("Received request for autopay mandate registration for merchant: {} with payload: {}", merchant.getId(), requestDTO);
        UPIRegisterResponseDto response = autoPayUPIService.registerMandate(merchant, requestDTO);
        log.info("Response for autopay mandate registration for merchant: {} is: {}", merchant.getId(), response);
        return response;
    }

    @GetMapping(value = "/mandate/status")
    public MandateUPIStatusResponse statusCheckMandate(
            @RequestAttribute BasicDetailsDto merchant,
            @RequestParam (required = true) String orderId
    ) {
        log.info("received request for status check for orderId: {}", orderId);
        MandateUPIStatusResponse response = autoPayUPIService.checkStatus(merchant, orderId);
        log.info("response for status check for merchant: {} and orderId: {}, is: {}",merchant.getId(),orderId,response);
        return response;

    }

    @GetMapping(value = "/mandate/transaction")
    public FetchTxnResponseDto fetchTransaction(
            @RequestAttribute BasicDetailsDto merchant,
            @RequestParam(name = "page_num") Optional<Integer> pageNum,
            @RequestParam(name = "page_size") Optional<Integer> pageSize,
            @RequestParam Long loanId
    ) {

        autoPayUPIServiceValidator.validatePageData(pageNum,pageSize);
        return autoPayUPIService.fetchTransaction(merchant, loanId, pageNum.get(), pageSize.get());
    }

    @PutMapping(value = "/mandate/frequency")
    public ResponseEntity<Boolean> updateFrequency(
            @RequestAttribute BasicDetailsDto merchant,
            @RequestBody UpdateFrequencyRequestDto dto) {
        return ResponseEntity.ok(autoPayUPIService.updateFrequencyForMandate(merchant, dto));
    }


    @GetMapping(value = "/mandate/need-alt-account")
    public ResponseEntity<NewAccountRequiredResponseDto> checkConsecutiveError(
            @RequestAttribute BasicDetailsDto merchant,
            @RequestParam(required = false) Optional<Long> merchantId) {
        Long targetMerchantId = merchantId.orElse(merchant != null ? merchant.getId() : null);
        log.info("Received request for consecutive error check for merchantId: {}", targetMerchantId);
        Boolean result = autoPayUPIService.checkConsecutiveError(targetMerchantId);
        log.info("Response for consecutive error check for merchantId: {} is: {}", targetMerchantId, result);
        String message = result ? "Bank account change required" : "Bank account change not required";
        NewAccountRequiredResponseDto response = NewAccountRequiredResponseDto.builder()
                .action(result)
                .message(message)
                .build();
        return ResponseEntity.ok(response);

    @GetMapping(value = "/required")
    public ResponseEntity<AutoPayRequiredDto> upiAutoPayRequired(@RequestAttribute BasicDetailsDto merchant){
        return ResponseEntity.ok(autoPayUPIService.isUPIAutoPayRequired(merchant));
    }

    @GetMapping(value = "/alt-mandate-eligibility")
    public ResponseEntity<UPIAltEligibilityDto> checkAltAccountEligibility(@RequestHeader String token,
                                                                           @RequestAttribute BasicDetailsDto merchant, @RequestParam Long applicationId) {
        log.info("Received request for alt mandate eligibility check for merchantId: {}, applicationId: {}", merchant.getId(), applicationId);
        return ResponseEntity.ok(autoPayUPIService.checkAltMandateEligibility(applicationId, merchant.getId(), token));
    }

    @PostMapping(value = "/register/alt-mandate")
    public UPIRegisterResponseDto registerAutoPayForAltMandate(@RequestAttribute BasicDetailsDto merchant,
                                                               @RequestBody AutoPayUPIAltMandateRegisterRequest requestDto) {
        log.info("Received request for alt mandate registration for merchantId: {} with payload: {}", merchant.getId(), requestDto);
        UPIRegisterResponseDto response = autoPayUPIService.registerAltMandate(merchant, requestDto);
        log.info("Response for alt mandate registration for merchantId: {} is: {}", merchant.getId(), response);
        return response;

    }
}
