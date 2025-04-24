package com.bharatpe.lending.lendingplatform.underwriting.controller;

import com.bharatpe.lending.dto.GlobalLimitResponse;
import com.bharatpe.lending.enums.EligibilityRequestSource;
import com.bharatpe.lending.service.APIGatewayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("underwriting")
@RequiredArgsConstructor
public class UnderwritingController {
    private final APIGatewayService apiGatewayService;

    @GetMapping("/eligibility")
    public ResponseEntity<GlobalLimitResponse> getEligibility(@RequestParam Long merchantId,
                                                              @RequestParam String source,
                                                              @RequestParam Integer appVersion,
                                                              @RequestParam Boolean clubV2,
                                                              @RequestParam Boolean useCache,
                                                              @RequestParam Boolean isPincodeChanged,
                                                              @RequestParam String sessionId,
                                                              @RequestParam Boolean flagForUwToSkipCache,
                                                              @RequestParam EligibilityRequestSource clientIdentifier) {
        GlobalLimitResponse globalLimitResponse = apiGatewayService.getScenapticGlobalLimit(merchantId, source, appVersion, clubV2, useCache, isPincodeChanged,
                sessionId, flagForUwToSkipCache, clientIdentifier);
        return ResponseEntity.ok(globalLimitResponse);

    }
}
