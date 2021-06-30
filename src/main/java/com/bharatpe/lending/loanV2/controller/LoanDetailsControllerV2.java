package com.bharatpe.lending.loanV2.controller;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV2.dto.LoanDetailsRequest;
import com.bharatpe.lending.loanV2.service.LoanDetailsServiceV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("lending")
@Slf4j
public class LoanDetailsControllerV2 {

    @Autowired
    LoanDetailsServiceV2 loanDetailsServiceV2;

    @PostMapping(value = "/loanDetails/v2")
    public ResponseEntity<ApiResponse<?>> getLoanDetails(@RequestHeader("token") String token, @RequestAttribute Merchant merchant, @RequestBody(required = false) LoanDetailsRequest loanDetailsRequest){
        log.info("loan details v2 request:{} for merchant:{}", loanDetailsRequest, merchant.getId());
        ApiResponse<?> response;
        try {
            response = loanDetailsServiceV2.getLoanDetails(loanDetailsRequest, merchant, token);
        } catch (Exception e) {
            log.error("Exception in loan details v2 for merchant:{}", merchant.getId(), e);
            response = new ApiResponse<>(false, "Something went wrong");
        }
        log.info("loan details v2 response:{} for merchant:{}", response, merchant.getId());
        return ResponseEntity.ok().body(response);
    }

    @GetMapping(value = "/enachBanks")
    public ResponseEntity<ApiResponse<?>> getEnachBankList() {
        return ResponseEntity.ok(loanDetailsServiceV2.getEnachBanks());
    }
}
