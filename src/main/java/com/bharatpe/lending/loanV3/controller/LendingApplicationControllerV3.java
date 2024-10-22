package com.bharatpe.lending.loanV3.controller;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV3.dto.*;
import com.bharatpe.lending.loanV3.services.LendingApplicationServiceV3Base;
import com.bharatpe.lending.loanV3.services.ModifyStageService;
import com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl.PiramalGetLoanDetails;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("lending/v3/")
@Slf4j
public class LendingApplicationControllerV3 {

    private LendingApplicationServiceV3Base lendingApplicationServiceV3;
    private ModifyStageService modifyStageService;
    private NbfcUtils nbfcUtils;

    @Autowired
    PiramalGetLoanDetails piramalGetLoanDetails;

    @Autowired
    public LendingApplicationControllerV3(@Qualifier("lendingApplicationServiceV3Impl") LendingApplicationServiceV3Base lendingApplicationServiceV3,
                                          ModifyStageService modifyStageService, NbfcUtils nbfcUtils
    ) {
        this.lendingApplicationServiceV3 = lendingApplicationServiceV3;
        this.modifyStageService = modifyStageService;
        this.nbfcUtils = nbfcUtils;
    }


    @GetMapping("/application/creationStatus")
    public ResponseEntity<ApiResponse<?>> applicationStatus(@RequestAttribute BasicDetailsDto merchant, @RequestParam(required = false) Long associationId, @RequestParam(required = false) String lenderKycStatus) {
        log.info("fetch application creation v3 status request for {} of merchant:{}", associationId, merchant.getId());
        ApiResponse<?> response = lendingApplicationServiceV3.fetchApplicationStatus(merchant.getId(), lenderKycStatus);
        log.info(" application creation status response:{} for merchant:{}", response, merchant.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/invoke/lenderAssociation")
    public ResponseEntity<ApiResponse<?>> invokeLenderAssociation(@RequestAttribute BasicDetailsDto merchant, @RequestBody InvokeLenderAssociationRequest invokeLenderAssociationRequest) {
        log.info("invoke lender association request for application {} of merchant:{}", invokeLenderAssociationRequest.getApplicationId(), merchant.getId());
        invokeLenderAssociationRequest.setForceEnable(ObjectUtils.isEmpty(invokeLenderAssociationRequest.getForceEnable()) ? false : invokeLenderAssociationRequest.getForceEnable());
        lendingApplicationServiceV3.initLenderAssociation(invokeLenderAssociationRequest);
        return ResponseEntity.ok(new ApiResponse<>(true,"next stage invoked for lender"));
    }

    //
    @PostMapping("/modify/application")
    public ResponseEntity<ApiResponse<?>> invokeLenderAssociation(@RequestBody ModifyAppRequest modifyRequest) {
        log.info("modify app request {}", modifyRequest);
        if ("2".equalsIgnoreCase(modifyRequest.getVersion())) {
            return ResponseEntity.ok(lendingApplicationServiceV3.modifyAppDetailsV2(modifyRequest));
        }
        return ResponseEntity.ok(lendingApplicationServiceV3.modifyAppDetails(modifyRequest));
    }

    @PostMapping("/modifyLender")
    public ResponseEntity<ModifyLenderDto> modifyLender(@RequestBody ModifyLenderDto modifyLenderDto) {
        log.info("Initiated the modify lender request {}", modifyLenderDto);
        modifyStageService.modifyLender(modifyLenderDto);
        return ResponseEntity.ok().body(modifyLenderDto);
    }

    @PostMapping("/nextStage")
    public ResponseEntity<PushApplicationNextStageDto> pushApplicationToNextStage(@RequestBody PushApplicationNextStageDto pushApplicationNextStageDto) {
        log.info("initiated the push to next stage request {}", pushApplicationNextStageDto);
        modifyStageService.pushToNextStageAsync(pushApplicationNextStageDto);
        return ResponseEntity.ok().body(pushApplicationNextStageDto);
    }

    @GetMapping("/loan/details")
    public ResponseEntity<ApiResponse<?>> getLoanDetails(@RequestParam Long applicationId) {
        log.info("fetch loan details request for {}", applicationId);
        ApiResponse<?> response = new ApiResponse<>(piramalGetLoanDetails.getLoanDetails(applicationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/invokeStage")
    public ResponseEntity<?> invokeStage(@RequestBody InvokeStageRequestDTO invokeStageRequest) {
        log.info("initiated the invoke stage request {}", invokeStageRequest);
        ApiResponse<?> response = lendingApplicationServiceV3.invokeStageForLender(invokeStageRequest);
        HttpStatus status = response.success ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/lender/eKyc")
    public ResponseEntity<?> lenderEkyc(@RequestBody InvokeStageRequestDTO invokeStageRequest) {
        log.info("initiated lender eKyc request {}", invokeStageRequest);
        ApiResponse<?> response = lendingApplicationServiceV3.initiateLenderEKyc(invokeStageRequest);
        HttpStatus status = response.success ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping ("/modifyOffer")
    ResponseEntity<?> modifyOffer(@RequestBody Map<String, Long> map, @RequestAttribute BasicDetailsDto merchant){
        log.info("modify offer request : {}", map);
        if (ObjectUtils.isEmpty(merchant) || ObjectUtils.isEmpty(map) || !map.containsKey("applicationId")){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(false,"required parameters are missing"));
        }
        ApiResponse<?> response =lendingApplicationServiceV3.modifyOffer(map.get("applicationId"), merchant.getId());
        return ResponseEntity.ok(response);
    }

}
