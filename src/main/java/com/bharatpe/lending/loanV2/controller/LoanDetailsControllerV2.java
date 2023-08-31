package com.bharatpe.lending.loanV2.controller;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dto.UpdateMerchantReferencesRequestDto;
import com.bharatpe.lending.dto.ValidateMerchantReferencesRequestDto;
import com.bharatpe.lending.dto.LendingMerchantPermissionsDto;
import com.bharatpe.lending.exception.BureauCallMaskedApiException;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.loanV2.dto.EligibilityIframeConsumptionDTO;
import com.bharatpe.lending.loanV2.dto.LatestLoanDetailResponse;
import com.bharatpe.lending.loanV2.dto.LoanDetailsRequest;
import com.bharatpe.lending.loanV2.service.LoanDetailsServiceV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("lending")
@Slf4j
public class LoanDetailsControllerV2 {

    @Autowired
    LoanDetailsServiceV2 loanDetailsServiceV2;

    @Autowired
    MerchantService merchantService;

    @PostMapping(value = "/loanDetails/v2", produces="application/json")
    public ResponseEntity<ApiResponse<?>> getLoanDetails(@RequestHeader(value = "token", required = false) String token, @RequestAttribute(required = false) BasicDetailsDto merchant,
                                                         @RequestBody(required = false) LoanDetailsRequest loanDetailsRequest,
                                                         @RequestParam(required = false) Long merchantId) throws BureauCallMaskedApiException {

        if (ObjectUtils.isEmpty(merchant)) {
            final Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(merchantId);
            if (basicDetailsDto.isPresent())
                merchant = basicDetailsDto.get();
            else {
                return new ResponseEntity<>(null, HttpStatus.UNAUTHORIZED);
            }
        }

        log.info("loan details v2 request:{} for merchant:{}", loanDetailsRequest, merchant.getId());
        ApiResponse<?> response;
        try {
            response = loanDetailsServiceV2.getLoanDetails(loanDetailsRequest, merchant, token);
        } catch (BureauCallMaskedApiException e){
            throw (e);
        } catch (Exception e) {
            log.error("Exception in loan details v2 for merchant:{}", merchant.getId(), e);
            response = new ApiResponse<>(false, "Something went wrong");
        }
        log.info("loan details v2 response:{} for merchant:{}", response, merchant.getId());
        return ResponseEntity.ok().body(response);
    }

    @GetMapping(value = "/enachBanks")
    public ResponseEntity<ApiResponse<?>> getEnachBankList() {
        return ResponseEntity.ok(loanDetailsServiceV2.getEnachBanks());
    }

    @GetMapping(value = "/businessCategorySubCategory")
    public ResponseEntity<ApiResponse<?>> getBusinessCategoryDetails(@RequestAttribute BasicDetailsDto merchant) {
        log.info("Fetching business Details for merchantId:{}",merchant.getId());
        return ResponseEntity.ok(loanDetailsServiceV2.getBusinessCategorySubCategory(merchant.getId()));
    }
    @GetMapping(value = "/getLatestLoanDetails")
    public ResponseEntity<ApiResponse<?>> getLatestLoanDetails(@RequestParam Long merchantId) {
        log.info("Fetching latest loan Details for merchantId:{}",merchantId);
        ApiResponse<LatestLoanDetailResponse> apiResponse = loanDetailsServiceV2.getLatestLoanDetails(merchantId);
        return ResponseEntity.ok(apiResponse);
    }

    @PostMapping(value="/getCreditScoreReportDetail")
    public ResponseEntity<ApiResponse<?>> getCreditScoreReportDetail(@RequestAttribute BasicDetailsDto merchant, @RequestBody CommonAPIRequest commonAPIRequest){
        ApiResponse<?> apiResponse= loanDetailsServiceV2.getCreditScoreReportDetail(merchant,commonAPIRequest);
        return ResponseEntity.ok(apiResponse);
    }

    @PostMapping(value="/getMaskedMobileNos")
    public ResponseEntity<ApiResponse<?>> getMaskedMobileNos(@RequestAttribute BasicDetailsDto merchant, @RequestBody CommonAPIRequest commonAPIRequest){
        ApiResponse<?> apiResponse= loanDetailsServiceV2.getMaskedMobileNos(merchant,commonAPIRequest);
        return ResponseEntity.ok(apiResponse);
    }

    @PostMapping(value="/getLoanAndCreditCardDetail")
    public ResponseEntity<ApiResponse<?>> getLoanAndCreditCardDetail(@RequestAttribute BasicDetailsDto merchant, @RequestBody CommonAPIRequest commonAPIRequest){
        ApiResponse<?> apiResponse= loanDetailsServiceV2.getLoanAndCreditCardDetail(merchant,commonAPIRequest);
        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping(value = "/getLoanDashboardDetails")
    public ResponseEntity<ApiResponse<?>> getDashboardDetails(@RequestAttribute(required = true) BasicDetailsDto merchant,
                                                              @RequestParam Boolean isIOS) {
        if(Objects.isNull(merchant)) {
            log.info("merchant not found");
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(loanDetailsServiceV2.getLoanDashboardDetails(merchant, isIOS));
    }


    @GetMapping(value = "/getMerchantPermissions")
    public ResponseEntity<ApiResponse<?>> getMerchantPermissionDetails(@RequestAttribute(required = true) BasicDetailsDto merchant) {
        log.info("Start getting Lending merchant permission details of merchantId: {}", merchant.getId());
        if (Objects.isNull(merchant)) {
            log.info("merchant not found");
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(loanDetailsServiceV2.getMerchantPermissions(merchant));
    }

    @PostMapping(value = "/updateMerchantPermissions")
    public ResponseEntity<ApiResponse<?>> updateMerchantPermissionDetails(@RequestAttribute(required = true) BasicDetailsDto merchant,
                                                                          @RequestBody LendingMerchantPermissionsDto lendingMerchantPermissionsDto) {
        log.info("Start updating Lending merchant permission  details: {} of merchantId: {}", lendingMerchantPermissionsDto, merchant.getId());
        if (Objects.isNull(merchant)) {
            log.info("merchant not found");
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(loanDetailsServiceV2.updateMerchantPermissions(merchant, lendingMerchantPermissionsDto));
    }

    @GetMapping(value = "/getMerchantReferences")
    public ResponseEntity<ApiResponse<?>> getMerchantReferences(@RequestAttribute(required = true) BasicDetailsDto merchant) {
        if (Objects.isNull(merchant) || Objects.isNull(merchant.getId())) {
            log.info("merchant not found");
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
        log.info("Start getting merchant references of merchantId: {}", merchant.getId());
        return ResponseEntity.ok(loanDetailsServiceV2.getMerchantReferences(merchant));
    }

    @PostMapping(value = "/validateMerchantReferences")
    public ResponseEntity<ApiResponse<?>> validateMerchantReferences(@RequestAttribute(required = true) BasicDetailsDto merchant, @RequestBody List<ValidateMerchantReferencesRequestDto> referenceList) {
        if (Objects.isNull(merchant) || Objects.isNull(merchant.getId())) {
            log.info("merchant not found");
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
        if (Objects.isNull(referenceList)) {
            log.info("request body not found!");
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
        log.info("Validating merchant references of merchantId: {}", merchant.getId());
        return ResponseEntity.ok(loanDetailsServiceV2.validateMerchantReferences(merchant, referenceList));
    }

    @PostMapping(value = "/updateMerchantReferences")
    public ResponseEntity<ApiResponse<?>> updateMerchantReferences(@RequestAttribute(required = true) BasicDetailsDto merchant, @RequestBody UpdateMerchantReferencesRequestDto requestDto) {
        if (Objects.isNull(merchant) || Objects.isNull(merchant.getId())) {
            log.info("merchant not found");
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
        log.info("Updating merchant references of merchantId: {}", merchant.getId());
        return ResponseEntity.ok(loanDetailsServiceV2.updateMerchantReferences(merchant, requestDto));
    }

    @GetMapping(value = "/getEligibilityIframe")
    public ResponseEntity<ApiResponse<?>> getIframeDetails(@RequestAttribute(required = true) BasicDetailsDto merchant, @RequestParam String client) {
        if (Objects.isNull(merchant)  || Objects.isNull(merchant.getId()) || Objects.isNull(client)) {
            log.info("Incorrect request details");
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
        log.info("Fetching Iframe details for merchant : {}", merchant.getId());
        return ResponseEntity.ok(loanDetailsServiceV2.getIframeDetails(merchant.getId(), client));
    }

    @PostMapping(value = "/iframeBannerConsumed")
    public ResponseEntity<ApiResponse<?>> iframeBannerConsumed(@RequestAttribute(required = true) BasicDetailsDto merchant, @Valid @RequestBody EligibilityIframeConsumptionDTO requestDto) {
        if (Objects.isNull(merchant) || Objects.isNull(merchant.getId())) {
            log.info("merchant not found");
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
        log.info("posting iframe consumption event for merchantId: {}", merchant.getId());
        return ResponseEntity.ok(loanDetailsServiceV2.iframeBannerConsumption(merchant.getId(), requestDto));
    }

    @GetMapping(value = "/get/underwriting-doc/eligibility")
    public ResponseEntity<ApiResponse<?>> getUnderWritingDocEligibility(
            @RequestParam(name = "type") String docType,
            @RequestParam(name = "statusCheck", required = false) boolean statusCheck,
            @RequestParam(name = "event", required = false) String event,
            @RequestAttribute BasicDetailsDto merchant
    ) {
        return ResponseEntity.ok(loanDetailsServiceV2.underwritingDocsEligibility(merchant.getId(), docType, statusCheck, event));
    }
}