package com.bharatpe.lending.loanV3.controller;

import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.service.impl.EdiModelAssignmentV1;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("lending/v3/test/")
@Slf4j
public class TestControllerDemo {

    @Autowired
    EdiModelAssignmentV1 ediModelAssignmentV1;

    @GetMapping("ediModel")
    public ResponseEntity<ApiResponse<?>> getEdiModel(@RequestParam Long merchantId) {
        log.info(" received via controller {}", merchantId);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(true,ediModelAssignmentV1.assignModel(merchantId).name()));

    }
}
