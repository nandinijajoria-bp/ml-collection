package com.bharatpe.lending.loanV3.revamp.controller;


import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("lending")
public class LoanDashboardController {

    @Autowired
    private LoanDashboardService loanDashboardService;

    @GetMapping(value = "/v2/version-details", produces="application/json")
    public ResponseEntity<ApiResponse<?>> getApiVersionDetails(@RequestAttribute BasicDetailsDto merchant) {
        return ResponseEntity.ok().body(new ApiResponse<>(loanDashboardService.getLoanDashboardApiVersion(merchant)));
    }


    @GetMapping(value = "/v2/loan-dashboard-details", produces="application/json")
    public ResponseEntity<ApiResponse<?>> getLoanDashboardDetails(@RequestAttribute BasicDetailsDto merchant) {
        return ResponseEntity.ok().body(new ApiResponse<>(loanDashboardService.getLoanDashboardDetails(merchant)));
    }


}
