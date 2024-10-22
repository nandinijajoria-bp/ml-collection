package com.bharatpe.lending.loanV3.controller;

import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV3.consumer.*;
import com.bharatpe.lending.loanV3.dto.*;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcResponseDto;

import com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl.InsurancePolicyDocService;
import com.bharatpe.lending.loanV3.services.associationsV2.wrapper.*;
import com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl.ESignDocService;
import com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl.PiramalLoanCallbackService;
import com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl.RiskDecisionAsyncService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("lending/v3/callback/")
@Slf4j
public class NbfcCallbackControllerV3 {

    @Autowired
    BreRequestKafka breRequestKafka;

    @Autowired
    KycRequestKafka kycRequestKafka;

    @Autowired
    DrawdownRequestKafka drawdownRequestKafka;

    @Autowired
    SancWrapperRequestKafka sancWrapperRequestKafka;

    @Autowired
    DataUploadRequestKafka dataUploadRequestKafka;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    PiramalLoanCallbackService createLoanCallbackService;

    @Autowired
    RiskDecisionAsyncService riskDecisionAsyncService;

    @Autowired
    ESignDocService eSignDocService;

    @Autowired
    DisbursalCallbackWrapperService disbursalCallbackWrapperService;

    @Autowired
    BreCallbackWrapperService breCallbackWrapperService;

    @Autowired
    KycCallbackWrapperService kycCallbackWrapperService;

    @Autowired
    DigitalSignCallbackWrapperService digitalSignCallbackWrapperService;

    @Autowired
    InsurancePolicyDocService insurancePolicyDocService;

    @Autowired
    PennyDropCallbackWrapperService pennyDropCallbackWrapperService;


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

    @PostMapping("sanction")
    public ResponseEntity<ApiResponse<?>> listenSanctionCallback(@RequestBody SanctionCallbackResponseDto sanctionCallbackResponseDto) throws JsonProcessingException {
        log.info("sanction callback received via controller {}", sanctionCallbackResponseDto);
        sancWrapperRequestKafka.sanctionCallbackListener(objectMapper.writeValueAsString(sanctionCallbackResponseDto));
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(true,"sanction event consumed successfully !"));
    }

    @PostMapping("drawdown")
    public ResponseEntity<ApiResponse<?>> listenDrawdownCallback(@RequestBody DrawdownCallbackResponseDto drawdownCallbackResponseDto) throws JsonProcessingException {
        log.info("drawdown callback received via controller {}", drawdownCallbackResponseDto);
        drawdownRequestKafka.drawdownEventListener(objectMapper.writeValueAsString(drawdownCallbackResponseDto));
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(true,"drawdown event consumed successfully !"));
    }

    @PostMapping("invoke/dataUpload")
    public ResponseEntity<String> dataUploadApi(@RequestBody Long applicationId) {
        log.info("invoke data upload request for application id {}",applicationId);
        Map<String,String> request = new HashMap(){{
            put("application_id", applicationId.toString());
        }};
        try {
            dataUploadRequestKafka.invokeDocUpload(objectMapper.writeValueAsString(request));
        } catch (Exception e) {
            log.error("error in parsing request for  {}", applicationId, e);
        }
        return ResponseEntity.ok().body("Data upload request has been initiated");
    }

    @PostMapping("loan-decision")
    public ResponseEntity<ApiResponse<?>> postLoanDecision(@RequestBody NbfcResponseDto nbfcResponseDto) throws JsonProcessingException {
        log.info("loan creation callback received via controller {}", nbfcResponseDto);
        ApiResponse response = createLoanCallbackService.createLoanCallback(nbfcResponseDto);
        return ResponseEntity.status(response.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST ).body(response);

    }

    @PostMapping("disbursal-decision")
    public ResponseEntity<ApiResponse<?>> postDisbursalDecision(@RequestBody NbfcResponseDto nbfcResponseDto) throws JsonProcessingException {
        log.info("disbursal creation callback received via controller {}", nbfcResponseDto);
        ApiResponse response = createLoanCallbackService.disbursalCallback(nbfcResponseDto);
        return ResponseEntity.status(response.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST ).body(response);

    }


    @PostMapping("risk-decision")
    public ResponseEntity<ApiResponse<?>> riskAsyncCallback(@RequestBody NbfcResponseDto nbfcResponseDto) throws JsonProcessingException {
        log.info("risk async callback received via controller {}", nbfcResponseDto);
        riskDecisionAsyncService.invokeRiskDecision(nbfcResponseDto);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(true,"risk async callback handled!"));

    }

    @PostMapping("estamp-doc")
    public ResponseEntity<ApiResponse<?>> getEStampDoc(@RequestBody NbfcResponseDto nbfcResponseDto) throws JsonProcessingException {
        log.info("estamp doc callback received via controller {}", nbfcResponseDto);
        ApiResponse<?> response = eSignDocService.persistAndSendCommunicationForESignDoc(nbfcResponseDto);
        return ResponseEntity.status(response.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST ).body(response);
    }

    @PostMapping("bre-decision")
    public ResponseEntity<?> breCallback(@RequestBody NBFCResponseDTO nbfcResponseDTO) {
        log.info("bre-decision callback received via controller {}", nbfcResponseDTO);
        breCallbackWrapperService.breCallback(nbfcResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(true,"bre async callback handled"));
    }

    @PostMapping("drawdown-decision")
    public ResponseEntity<?> drawdownCallback(@RequestBody NBFCResponseDTO nbfcResponseDTO) {
        log.info("drawdown-decision callback received via controller {}", nbfcResponseDTO);
        disbursalCallbackWrapperService.disbursalCallback(nbfcResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(true,"drawdown async callback handled"));
    }

    @PostMapping("kyc-callback")
    public ResponseEntity<ApiResponse<?>> KycCallback(@RequestBody NBFCResponseDTO nbfcResponseDTO) throws JsonProcessingException {
        log.info("kyc-callback received via controller {}", nbfcResponseDTO);
        kycCallbackWrapperService.kycCallback(nbfcResponseDTO);
        return ResponseEntity.ok(new ApiResponse<>(true,"kyc async callback handled"));
    }

    @PostMapping("digital-sign-callback")
    public ResponseEntity<ApiResponse<?>> digitalSignCallback(@RequestBody NBFCResponseDTO nbfcResponseDTO) throws JsonProcessingException {
        log.info("digital-sign-callback received via controller {}", nbfcResponseDTO);
        digitalSignCallbackWrapperService.digitalSignCallback(nbfcResponseDTO);
        return ResponseEntity.ok(new ApiResponse<>(true,"kyc async callback handled"));
    }

    @PostMapping("insurance-doc")
    public ResponseEntity<ApiResponse<?>> getInsuranceDoc(@RequestBody NbfcResponseDto nbfcResponseDto) throws JsonProcessingException {
        log.info("Loan insurance doc callback received via controller {}", nbfcResponseDto);
        ApiResponse<?> response = insurancePolicyDocService.uploadInsurancePolicyDoc(nbfcResponseDto);
        return ResponseEntity.status(response.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST ).body(response);
    }

    @PostMapping("eKyc")
    public ResponseEntity<ApiResponse<?>> listenEKycCallback(@RequestBody EKycCallbackResponseDto eKycCallbackResponseDto) throws JsonProcessingException {
        log.info("eKyc callback received via controller {}", eKycCallbackResponseDto);
        kycRequestKafka.eKycCallbackListener(objectMapper.writeValueAsString(eKycCallbackResponseDto));
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(true,"eKyc event consumed successfully !"));
    }

    @PostMapping("eKyc-decision")
    public ResponseEntity<ApiResponse<?>> eKycCallback(@RequestBody NBFCResponseDTO nbfcResponseDTO) {
        log.info("eKyc callback received via controller {}", nbfcResponseDTO);
        kycCallbackWrapperService.lenderEKycCallback(nbfcResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(true,"eKyc callback consumed successfully !"));
    }

    @PostMapping("penny-drop")
    public ResponseEntity<ApiResponse<?>> listenPennyDropCallback(@RequestBody NBFCResponseDTO nbfcResponseDTO) throws JsonProcessingException {
        log.info("pennyDrop callback received via controller {}", nbfcResponseDTO);
        pennyDropCallbackWrapperService.pennyDropCallback(nbfcResponseDTO);
        return ResponseEntity.ok(new ApiResponse<>(true,"pennyDrop async callback handled"));
    }

}
