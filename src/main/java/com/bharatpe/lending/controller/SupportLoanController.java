package com.bharatpe.lending.controller;

import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.dto.SupportResponseDTO;
import com.bharatpe.lending.service.FosService;
import com.bharatpe.lending.service.SupportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("support")
public class SupportLoanController {
    Logger logger = LoggerFactory.getLogger(SupportLoanController.class);

    @Autowired
    SupportService supportService;

    @RequestMapping(value="/loan", method = RequestMethod.GET, produces="application/json")
    public ResponseEntity<SupportResponseDTO> supportLoanDetails(@RequestParam Long merchantId) {
        logger.info("Request received to get loan details for merchantId: {}", merchantId);
        return new ResponseEntity<>(supportService.supportLoan(merchantId), HttpStatus.OK);
    }
}

