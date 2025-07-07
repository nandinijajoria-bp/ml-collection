package com.bharatpe.lending.loanV3.controller;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dto.vkyc.response.VkycStatusResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.services.VKycService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping
public class VKycController {
    private final VKycService vKycService;

    @GetMapping("vkyc/initiate")
    public ResponseEntity<?> initiateVKyc(@RequestAttribute BasicDetailsDto merchant,
                                          @RequestParam(name = "application_id") Long applicationId,
                                          @RequestParam(name = "is_retry", required = false) Boolean isRetry,
                                          @RequestParam(value = "lender", required = false) String lender) {
        return ResponseEntity.ok(vKycService.initiateVKyc(merchant.getId(), applicationId, lender, isRetry));
    }

    @GetMapping("vkyc/status")
    public ResponseEntity<?> statusCheck(@RequestAttribute BasicDetailsDto merchant,
                                         @RequestParam(name = "application_id") Long applicationId,
                                         @RequestParam(value = "lender", required = false) String lender) {
        return ResponseEntity.ok(vKycService.statusCheck(merchant.getId(), applicationId, lender));
    }

    @PostMapping("vkyc/callback")
    public ResponseEntity<?> vKycCallback(@RequestBody NBFCResponseDTO<VkycStatusResponseDto> callbackRequest) {
        vKycService.consumeVKycCallback(callbackRequest);
        return ResponseEntity.ok("Successfully consumed VKyc callback");
    }

    @GetMapping("dkyc/initiate")
    public ResponseEntity<?> initiateDKyc(@RequestAttribute BasicDetailsDto merchant,
                                          @RequestParam(name = "application_id") Long applicationId,
                                          @RequestParam(value = "lender", required = false) String lender) {
        return ResponseEntity.ok(vKycService.initiateDkyc(merchant.getId(), applicationId, lender));
    }

}
