package com.bharatpe.lending.controller;

import com.bharatpe.lending.dto.ClosePreviousLoanForTopupDTO;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.service.VerifyOTPService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("topup")
public class TopupController {

    @Autowired
    VerifyOTPService verifyOTPService;

    @RequestMapping(value="/close", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public ResponseEntity<?> closePreviousApplicaitonForTopup(@RequestBody ClosePreviousLoanForTopupDTO requestDTO) {
        log.info("request Body for closePreviousApplicaitonForTopup : {}", requestDTO);
        final Map<String, Object> response = verifyOTPService.closePreviousLoanAfterSuccessfulTopupCreation(requestDTO.getApplicationId());
        return new ResponseEntity<>(new ApiResponse<>(response), HttpStatus.OK);
    }
}
