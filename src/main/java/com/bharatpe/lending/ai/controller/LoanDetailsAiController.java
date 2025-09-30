package com.bharatpe.lending.ai.controller;

import com.bharatpe.lending.ai.dto.LoanDetailResponse;
import com.bharatpe.lending.ai.services.IAutoPayApiService;
import com.bharatpe.lending.ai.services.ILonaApplicationService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.entity.AutoPayUPI;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("ai/loan-details")
public class LoanDetailsAiController {

    private final ILonaApplicationService  lonaApplicationService;
    private  final IAutoPayApiService autoPayApiService;

    @GetMapping(value = "/latest",produces = "application/json")
    public ResponseEntity<ApiResponse<Object>> getApplicationDetail(
            @RequestAttribute(required = false) BasicDetailsDto merchant,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String intent) {
        if(merchant!=null){
            merchantId=merchant.getId();
        }
        log.info("request received with intent: {}", intent);
        if("upi_autopay".equalsIgnoreCase(intent)){
            log.info("Request received for autopay details for merchantId: {}", merchantId);
            Optional<AutoPayUPI> autoPayDetails = autoPayApiService.getAutoPayDetails(merchantId);
            return autoPayDetails.<ResponseEntity<ApiResponse<Object>>>map(autoPayUpi -> ResponseEntity.ok(new ApiResponse<>(autoPayUpi))).orElseGet(() -> ResponseEntity.ok(new ApiResponse<>(true, "no autopay details found")));
        }
        log.info("Request received to get loan application details for merchantId: {}", merchantId);
        LoanDetailResponse loanDetailResponse = lonaApplicationService.getLoanApplicationDetails(merchantId);
        if(loanDetailResponse==null){
            log.info("No loan application details found for merchantId: {}", merchantId);
            return ResponseEntity.ok(new ApiResponse<>(true, "no loan application found"));
        }
        log.info("Fetched loan application details for merchantId: {}, details are: {}", merchantId, loanDetailResponse);
        return ResponseEntity.ok(new ApiResponse<>(loanDetailResponse));
    }
}
