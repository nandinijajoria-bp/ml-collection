package com.bharatpe.lending.ai.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("ai/loan-details")
public class LoanDetailsAiController {

    @GetMapping(value = "/latest",produces = "application/json")
    public ResponseEntity<Object> getApplicationDetail(@RequestParam Long merchantId) {

        return ResponseEntity.ok("Loan application details for merchantId: " + merchantId);
    }
}
