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

    private static final String DEFAULT_PAGE_NUMBER = "0";
    private static final String DEFAULT_PAGE_SIZE = "10";
    private static final String DEFAULT_SORT_BY = "id";
    private static final String DEFAULT_SORT_DIRECTION = "asc";
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
      @RequestBody(required = true)
      RequestDTO<AutoUPIMandateRegisterRequestDto> requestDTO) {
        return autoPayUPIService.registerUPIForNewApplication(merchant, requestDTO);
    }


    @GetMapping(value = "/mandate/status")
    public MandateUPIStatusResponse statusCheckMandate(
            @RequestAttribute BasicDetailsDto merchant,
            @RequestParam (required = true) String orderId
    ) {
        return autoPayUPIService.checkStatus(merchant, orderId);
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


}
