package com.bharatpe.lending.loanV3.revamp.controller;


import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV3.revamp.response.EnachHistory;
import com.bharatpe.lending.loanV3.revamp.services.EnachService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("lending")
public class EnachController {


    @Autowired
    private EnachService enachService;

    @GetMapping(value = "/v1/enach-history", produces="application/json")
    public ResponseEntity<ApiResponse<?>> getApiVersionDetails(@RequestAttribute BasicDetailsDto merchant, @RequestParam Long applicationId) {
        return ResponseEntity.ok().body(new ApiResponse<>(enachService.getNachHisory(merchant,applicationId)));
    }
}
