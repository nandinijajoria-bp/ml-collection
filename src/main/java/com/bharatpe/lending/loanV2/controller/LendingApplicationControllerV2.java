package com.bharatpe.lending.loanV2.controller;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.dto.RequestCallbackDto;
import com.bharatpe.lending.loanV2.dto.*;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.service.APIGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("lending")
@Slf4j
public class LendingApplicationControllerV2 {

    @Autowired
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Autowired
    APIGatewayService apiGatewayService;

    @PostMapping(value = "/initiateKyc")
    public ResponseEntity<ApiResponse<?>> initiateKyc(@RequestAttribute Merchant merchant, @RequestBody InitiateKycRequest initiateKycRequest) {
        log.info("kyc initiate request:{} for merchant:{}", initiateKycRequest, merchant.getId());
        ApiResponse<?> response = lendingApplicationServiceV2.initiateKyc(merchant, initiateKycRequest);
        log.info("kyc initiate response:{} for merchant:{}", response, merchant.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/createApplication/v2")
    public ResponseEntity<ApiResponse<?>> createApplication(@RequestAttribute Merchant merchant, @RequestBody CreateApplicationRequest applicationRequest) {
        log.info("create application request:{} for merchant:{}", applicationRequest, merchant.getId());
        ApiResponse<?> response = lendingApplicationServiceV2.createApplication(merchant, applicationRequest);
        log.info("create application response:{} for merchant:{}", response, merchant.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/agreement/v2")
    public ResponseEntity<ApiResponse<?>> getAgreement(@RequestParam Long applicationId, @RequestAttribute Merchant merchant) {
        log.info("lending agreement v2 request:{} for merchant:{}", applicationId, merchant.getId());
        ApiResponse<?> response = lendingApplicationServiceV2.getAgreement(applicationId, merchant);
        log.info("lending agreement v2 response:{} for merchant:{}", response, merchant.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/applicationStatus/v2")
    public ResponseEntity<ApiResponse<?>> getApplicationStatus(@RequestHeader("token") String token, @RequestParam Long applicationId, @RequestParam(required = false) Boolean isIOS, @RequestAttribute Merchant merchant) {
        log.info("lending applicationStatus v2 request:{} for merchant:{}", applicationId, merchant.getId());
        ApiResponse<?> response = lendingApplicationServiceV2.getApplicationStatus(applicationId, merchant, isIOS, token);
        log.info("lending applicationStatus v2 response:{} for merchant:{}", response, merchant.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/application/resubmit")
    public ResponseEntity<ApiResponse<?>> resubmitApplication(@RequestBody(required = false) ResubmitApplicationDTO resubmitApplicationDTO){
        log.info("Lending application resubmit request:{} for merchant:{}",resubmitApplicationDTO,resubmitApplicationDTO.getMerchantId());
        ApiResponse<?> response = lendingApplicationServiceV2.resubmitApplication(resubmitApplicationDTO);
        log.info("Lending Resubmit Application Response:{} for applicationId:{}",response,resubmitApplicationDTO.getApplicationId());
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/application/resubmitDone")
    public ResponseEntity<ApiResponse<?>> resubmitDone(@RequestHeader("token") String token,@RequestParam Long applicationId, @RequestAttribute Merchant merchant){
        log.info("Lending application resubmit done merchantId:{} for applicationId:{}",merchant.getId(),applicationId);
        ApiResponse<?> response = lendingApplicationServiceV2.resubmitDone(merchant.getId(),applicationId);
        log.info("Lending Resubmit done Application Response:{} for applicationId:{}",response,applicationId);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/businessCategory")
    public ResponseEntity<ApiResponse<?>> getBusinessCategories(){
        ApiResponse<?> response = lendingApplicationServiceV2.getBusinessCategory();
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/businessDetails", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<?>> addBusinessDetails(@RequestBody BusinessDetailsDTO businessDetailsDTO, @RequestAttribute Merchant merchant){
        log.info("Adding business Details for merchantId:{}",merchant.getId(),businessDetailsDTO.toString());
        ApiResponse<?> response = lendingApplicationServiceV2.addBusinessDetails(businessDetailsDTO,merchant);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/requestCallback", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<?>> addCallbackRequest(@RequestBody RequestCallbackDto requestCallbackDto, @RequestAttribute Merchant merchant){
        log.info("Adding callback request for {}",requestCallbackDto.toString());
        ApiResponse<?> response = lendingApplicationServiceV2.addCallbackRequest(requestCallbackDto, merchant);
        return ResponseEntity.ok(response);
    }
}
