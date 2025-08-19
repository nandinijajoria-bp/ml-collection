package com.bharatpe.lending.ai.controller;

import com.bharatpe.lending.ai.dto.AiSupportLoanResponse;
import com.bharatpe.lending.ai.helper.AiResponseBuilder;
import com.bharatpe.lending.dto.SupportResponseDTO;
import com.bharatpe.lending.service.SupportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("ai/support")
public class SupportLoanAiController {
    private final SupportService supportService;

    @RequestMapping(value="/loan", method = RequestMethod.GET, produces="application/json")
    public ResponseEntity<AiSupportLoanResponse> supportLoanDetails(@RequestParam Long merchantId) {
        log.info("Request received to get loan details for merchantId: {}", merchantId);
        SupportResponseDTO supportResponse = supportService.supportLoan(merchantId);
        AiSupportLoanResponse aiResponse = AiResponseBuilder.getSupportResponse(supportResponse);
        return new ResponseEntity<>(aiResponse, HttpStatus.OK);
    }


}
