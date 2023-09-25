package com.bharatpe.lending.loanV3.controller;

import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV3.dto.LoanReceiptResponseDTO;
import com.bharatpe.lending.loanV3.services.associations.AbflReceiptService;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.impl.EdiModelAssignmentV1;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("lending/v3/test/")
@Slf4j
public class TestControllerDemo {

    @Autowired
    EdiModelAssignmentV1 ediModelAssignmentV1;

    @Autowired
    AbflReceiptService abflReceiptService;

    @Autowired
    APIGatewayService apiGatewayService;

    @GetMapping("ediModel")
    public ResponseEntity<ApiResponse<?>> getEdiModel(@RequestParam Long merchantId) {
        log.info(" received via controller {}", merchantId);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(true,ediModelAssignmentV1.assignModel(merchantId).name()));
    }

    @GetMapping("/postAbflReceipts")
    public ResponseEntity<Map> postAbflRepaymentReceipts(@RequestParam Long ledgerId) {
        log.info("postAbflReceipts for ledgerId : {}", ledgerId);
        boolean status = abflReceiptService.sendReceipt(ledgerId);
        return ResponseEntity.status(HttpStatus.OK).body(Collections.singletonMap("success", status));
    }

    @RequestMapping(value = "/ldc-top-consent", method = RequestMethod.POST)
    public ResponseEntity<?> ldcTopConsent(@RequestParam(name = "application_id") Long applicationId,
                                           @RequestParam(name = "to_be_paused") Boolean toBePaused,
                                           @RequestParam(name = "expected_foreclosure_amount") Double expectedForeclosureAmount) {
        return ResponseEntity.ok(apiGatewayService.getLdcTopupConsent(applicationId, toBePaused, expectedForeclosureAmount));
    }

    @RequestMapping(value = "/ldc-foreclosure_details", method = RequestMethod.GET)
    public ResponseEntity<?> ldcTopConsent(@RequestParam(name = "application_id") Long applicationId){
        return ResponseEntity.ok(apiGatewayService.getLdcForeclosureDetails(applicationId));
    }

}