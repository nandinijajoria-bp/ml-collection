package com.bharatpe.lending.controller;

import com.bharatpe.common.entities.ExperianAuditTrail;
import com.bharatpe.lending.dto.NbfcDecisionCallbackRequestDTO;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.NbfcCallbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;

@RestController
@RequestMapping("lending/nbfc")
@Slf4j
public class NbfcCallbackController {

    @Autowired
    NbfcCallbackService nbfcCallbackService;

    @Autowired
    APIGatewayService apiGatewayService;


    @PostMapping(value = "/mamta/decision/callback", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> decision(@RequestBody NbfcDecisionCallbackRequestDTO nbfcDecisionCallbackRequestDTO) {
        log.info("decision request with nbfcDecisionCallbackRequestDTO : {} ", nbfcDecisionCallbackRequestDTO.toString());

        if (ObjectUtils.isEmpty(nbfcDecisionCallbackRequestDTO) || ObjectUtils.isEmpty(nbfcDecisionCallbackRequestDTO.getPartnerLoanId()) || ObjectUtils.isEmpty(nbfcDecisionCallbackRequestDTO.getStatus())) {
            return new ResponseEntity<>(new ApiResponse<>(false, "Required fields partner_loan_id/status not sent"),
              HttpStatus.BAD_REQUEST);
        }

        final ApiResponse<?> apiResponse = nbfcCallbackService.processDecision(nbfcDecisionCallbackRequestDTO);

        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @GetMapping(value = "/testUploadAgreement", produces = "application/json")
    public ResponseEntity<?> testUploadAgreement() {
        log.info("test Upload agreement");
        apiGatewayService.uploadLoanAgreement(3673994L);
        return new ResponseEntity<>(new ApiResponse<String>("uploaded"), HttpStatus.OK);
    }

}
