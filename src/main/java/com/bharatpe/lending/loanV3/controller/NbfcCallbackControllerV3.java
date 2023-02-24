package com.bharatpe.lending.loanV3.controller;

import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV3.consumer.BreRequestKafka;
import com.bharatpe.lending.loanV3.consumer.KycRequestKafka;
import com.bharatpe.lending.loanV3.dto.BreCallbackResponseDto;
import com.bharatpe.lending.loanV3.dto.KycCallbackResponseDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("lending/v3/callback/")
@Slf4j
public class NbfcCallbackControllerV3 {

    @Autowired
    BreRequestKafka breRequestKafka;

    @Autowired
    KycRequestKafka kycRequestKafka;

    @Autowired
    ObjectMapper objectMapper;


    @PostMapping("bre")
    public ResponseEntity<ApiResponse<?>> listenBreCallback(@RequestBody BreCallbackResponseDto breCallbackResponseDto) throws JsonProcessingException {
        log.info("bre callback received via controller {}", breCallbackResponseDto);
        breRequestKafka.breCallbackListener(objectMapper.writeValueAsString(breCallbackResponseDto));
        return ResponseEntity.ok(null);
    }

    @PostMapping("kyc")
    public ResponseEntity<ApiResponse<?>> listenKycCallback(@RequestBody KycCallbackResponseDto kycCallbackResponseDto) throws JsonProcessingException {
        log.info("kyc callback received via controller {}", kycCallbackResponseDto);
        kycRequestKafka.kycCallbackListener(objectMapper.writeValueAsString(kycCallbackResponseDto));
        return ResponseEntity.ok(null);
    }
}
