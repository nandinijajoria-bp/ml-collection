package com.bharatpe.lending.loanV3.controller;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV3.dto.InvokeLenderAssociationRequest;
import com.bharatpe.lending.loanV3.dto.ModifyAppRequest;
import com.bharatpe.lending.loanV3.dto.ModifyLenderDto;
import com.bharatpe.lending.loanV3.dto.PushApplicationNextStageDto;
import com.bharatpe.lending.loanV3.services.LendingApplicationServiceV3Base;
import com.bharatpe.lending.loanV3.services.ModifyStageService;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("lending/v3/")
@Slf4j
public class LendingApplicationControllerV3 {

    private LendingApplicationServiceV3Base lendingApplicationServiceV3;
    private ModifyStageService modifyStageService;
    private NbfcUtils nbfcUtils;

    @Autowired
    public LendingApplicationControllerV3(@Qualifier("lendingApplicationServiceV3Impl") LendingApplicationServiceV3Base lendingApplicationServiceV3, ModifyStageService modifyStageService,  NbfcUtils nbfcUtils) {
        this.lendingApplicationServiceV3 = lendingApplicationServiceV3;
        this.modifyStageService = modifyStageService;
        this.nbfcUtils = nbfcUtils;
    }


    @GetMapping("/application/creationStatus")
    public ResponseEntity<ApiResponse<?>> applicationStatus(@RequestAttribute BasicDetailsDto merchant, @RequestParam(required = false) Long associationId) {
        log.info("fetch application creation v3 status request for {} of merchant:{}", associationId, merchant.getId());
        ApiResponse<?> response = lendingApplicationServiceV3.fetchApplicationStatus(merchant.getId());
        log.info(" application creation status response:{} for merchant:{}", response, merchant.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/invoke/lenderAssociation")
    public ResponseEntity<ApiResponse<?>> invokeLenderAssociation(@RequestAttribute BasicDetailsDto merchant, @RequestBody InvokeLenderAssociationRequest invokeLenderAssociationRequest) {
        log.info("invoke lender association request for application {} of merchant:{}", invokeLenderAssociationRequest.getApplicationId(), merchant.getId());
        invokeLenderAssociationRequest.setForceEnable(ObjectUtils.isEmpty(invokeLenderAssociationRequest.getForceEnable()) ? false : invokeLenderAssociationRequest.getForceEnable());
        lendingApplicationServiceV3.initLenderAssociation(invokeLenderAssociationRequest);
        return ResponseEntity.ok(null);
    }

    @PostMapping("/modify/application")
    public ResponseEntity<ApiResponse<?>> invokeLenderAssociation(@RequestBody ModifyAppRequest modifyRequest ) {
        log.info("modify app request {}", modifyRequest);
        return ResponseEntity.ok(lendingApplicationServiceV3.modifyAppDetails(modifyRequest));
    }

    @PostMapping("/modifyLender")
    public ResponseEntity<ModifyLenderDto> modifyLender(@RequestBody ModifyLenderDto modifyLenderDto){
        log.info("Initiated the modify lender request {}",modifyLenderDto);
        modifyStageService.modifyLender(modifyLenderDto);
        return ResponseEntity.ok().body(modifyLenderDto);
    }

    @PostMapping("/nextStage")
    public ResponseEntity<PushApplicationNextStageDto> pushApplicationToNextStage(@RequestBody PushApplicationNextStageDto pushApplicationNextStageDto){
        log.info("initiated the push to next stage request {}",pushApplicationNextStageDto);
        nbfcUtils.pushApplicationToNextStage(pushApplicationNextStageDto.getApplicationId(),pushApplicationNextStageDto.getLender(),
                pushApplicationNextStageDto.getLenderAssociationStage(),pushApplicationNextStageDto.getAutoInvoke());
        return ResponseEntity.ok().body(pushApplicationNextStageDto);
    }
}
