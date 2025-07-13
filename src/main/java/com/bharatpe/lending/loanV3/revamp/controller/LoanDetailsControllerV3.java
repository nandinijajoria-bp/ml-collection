package com.bharatpe.lending.loanV3.revamp.controller;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV3.revamp.dto.LoanDetailsV3Request;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController()
@RequestMapping("lending")
public class LoanDetailsControllerV3 {

    @Autowired
    LoanDetailsV3Service loanDetailsV3Service;

    @PostMapping(value = "/loanDetails/v3", produces="application/json")
    public ResponseEntity<ApiResponse<?>> getLoanDetails(@RequestHeader(value = "token", required = false) String token,
                                                         @RequestAttribute(required = false) BasicDetailsDto merchant,
                                                         @RequestBody(required = false) LoanDetailsV3Request request) {
        return ResponseEntity.ok().body(new ApiResponse<>(loanDetailsV3Service.getLoanDetails(request, merchant, token)));
    }

    @GetMapping(value = "/loanDetails/v3", produces="application/json")
    public ResponseEntity<ApiResponse<?>> getLoanDetails(@RequestHeader(value = "token", required = false) String token,
                                                         @RequestAttribute(required = false) BasicDetailsDto merchant,
                                                         @RequestParam(required = false) Long applicationId,
                                                         @RequestParam(required = false) String scope) {
        return ResponseEntity.ok().body(new ApiResponse<>(loanDetailsV3Service.getLoanDetailsWithoutScope(merchant, scope, applicationId, token)));
    }
}
