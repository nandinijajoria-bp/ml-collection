package com.bharatpe.lending.controller;


import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.service.AutoPayUPIService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auto-pay")
@Slf4j
public class AutoPayUPIController {

    public static final String DEFAULT_PAGE_NUMBER = "0";
    public static final String DEFAULT_PAGE_SIZE = "10";
    public static final String DEFAULT_SORT_BY = "id";
    public static final String DEFAULT_SORT_DIRECTION = "asc";
    @Autowired
    AutoPayUPIService autoPayUPIService;

    @PostMapping(value = "/register-mandate")
    public UPIRegisterResponseDto registerAutoPayForMerchant(
            @RequestAttribute BasicDetailsDto merchant,
            @RequestBody RequestDTO<UPIRegisterRequestDto> requestDTO) {
        return autoPayUPIService.registerUPI(merchant, requestDTO.getPayload().getLoanId(), requestDTO);

    }


    @GetMapping(value = "/status-check/mandate")
    public MandateUPIStatusResponse statusCheckMandate(
            @RequestAttribute BasicDetailsDto merchant,
            @RequestParam String orderId
    ) {
        return autoPayUPIService.checkStatus(merchant, orderId);
    }

    @GetMapping(value = "/fetch-transaction")
    public FetchTxnResponseDto fetchTransaction(
            @RequestAttribute BasicDetailsDto merchant,
            @RequestParam(name = "page_num") Integer pageNum,
            @RequestParam(name = "page_size") Integer pageSize,
            @RequestParam Long loanId
    ) {
        return autoPayUPIService.fetchTransaction(merchant, loanId, pageNum, pageSize);

    }

    @PutMapping(value = "/update/frequency")
    public ResponseEntity<Boolean> updateFrequency(@RequestParam BasicDetailsDto merchant, @RequestBody UpdateFrequencyRequestDto dto) {
        Boolean response = autoPayUPIService.updateFrequencyForMandate(merchant, dto);
        return ResponseEntity.ok(response);
    }


}
