package com.bharatpe.lending.controller;

import com.bharatpe.lending.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dto.LoanSurveyHeaderDto;
import com.bharatpe.lending.dto.LoanSurveyRequestDto;
import com.bharatpe.lending.service.LoanSurveyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("lending/loan_survey")
public class LoanSurveyController {

    Logger logger = LoggerFactory.getLogger(LoanSurveyController.class);

    @Autowired
    LoanSurveyService loanSurveyService;

    @GetMapping(value = "/info", produces = "application/json")
    public ResponseEntity<LoanSurveyHeaderDto> information(@RequestAttribute BasicDetailsDto merchant) {
        LoanSurveyHeaderDto dto =  loanSurveyService.getSurveyMerchantHeader(merchant);
        if(dto == null) return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @PostMapping(value = "/submit", produces = "application/json")
    public ResponseEntity<LoanSurveyRequestDto> submitSurvey(@RequestAttribute BasicDetailsDto merchant,
                                                        @RequestBody LoanSurveyRequestDto loanSurveyRequestDto) {
        logger.info("LoanSurveyRequestDto answer request for merchant:{} and loanSurveyRequestDto:{}",
            merchant.getId(), loanSurveyRequestDto);
        return new ResponseEntity<>(loanSurveyService.submitSurvey(merchant, loanSurveyRequestDto), HttpStatus.OK);
    }
}
