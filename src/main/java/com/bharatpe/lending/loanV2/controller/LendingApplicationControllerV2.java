package com.bharatpe.lending.loanV2.controller;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV2.dto.CreateApplicationRequest;
import com.bharatpe.lending.loanV2.dto.InitiateKycRequest;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("lending")
@Slf4j
public class LendingApplicationControllerV2 {

    @Autowired
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @PostMapping(value = "/initiateKyc")
    public ResponseEntity<ApiResponse<?>> initiateKyc(@RequestAttribute Merchant merchant, @RequestBody InitiateKycRequest initiateKycRequest) {
        log.info("kyc initiate request:{} for merchant:{}", initiateKycRequest, merchant.getId());
        ApiResponse<?> response = lendingApplicationServiceV2.initiateKyc(merchant, initiateKycRequest);
        log.info("kyc initiate response:{} for merchant:{}", response, merchant.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/createApplication/v2")
    public ResponseEntity<ApiResponse<?>> createApplication(@RequestAttribute Merchant merchant, @RequestBody CreateApplicationRequest applicationRequest) {
        log.info("create application request:{} for merchant:{}", applicationRequest, merchant.getId());
        ApiResponse<?> response = lendingApplicationServiceV2.createApplication(merchant, applicationRequest);
        log.info("create application response:{} for merchant:{}", response, merchant.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/agreement/v2")
    public ResponseEntity<ApiResponse<?>> getAgreement(@RequestParam Long applicationId, @RequestAttribute Merchant merchant) {
        log.info("lending agreement v2 request:{} for merchant:{}", applicationId, merchant.getId());
        ApiResponse<?> response = lendingApplicationServiceV2.getAgreement(applicationId, merchant);
        log.info("lending agreement v2 response:{} for merchant:{}", response, merchant.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/applicationStatus/v2")
    public ResponseEntity<ApiResponse<?>> getApplicationStatus(@RequestHeader("token") String token, @RequestParam Long applicationId, @RequestParam(required = false) Boolean isIOS, @RequestAttribute Merchant merchant) {
        log.info("lending applicationStatus v2 request:{} for merchant:{}", applicationId, merchant.getId());
        ApiResponse<?> response = lendingApplicationServiceV2.getApplicationStatus(applicationId, merchant, isIOS, token);
        log.info("lending applicationStatus v2 response:{} for merchant:{}", response, merchant.getId());
        return ResponseEntity.ok(response);
    }
}
