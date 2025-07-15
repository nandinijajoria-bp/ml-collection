package com.bharatpe.lending.controller;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.exception.CustomLendingException;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.service.LoanAndRTEEligibilityComputeService;
import com.bharatpe.lending.service.MileStoneProgramService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/milestone")
@Slf4j
public class MileStoneProgramController {

    @Autowired
    MileStoneProgramService mileStoneProgramService;

    @Autowired
    private LoanAndRTEEligibilityComputeService loanAndRTEEligibilityComputeService;

    @GetMapping(value = "/eligibility")
    public ApiResponse<MileStoneEligibilityResponseDto> computeEligibility(
            @RequestAttribute BasicDetailsDto merchant,
            @RequestParam(name = "loanAmount", required = false) String loanAmount
    ) {
        return mileStoneProgramService.checkEligibility(merchant, loanAmount);
    }


    @GetMapping(value = "/get/program-summary")
    public ApiResponse<DSMileStoneResponse> checkProgramSummary(@RequestAttribute BasicDetailsDto merchant) {
        return mileStoneProgramService.programSummary(merchant);

    }


    @PostMapping(value="/create-session")
    public ApiResponse<?> createMileStoneSession(@RequestAttribute BasicDetailsDto merchant,
                                       @RequestBody DSMileStoneResponse dsMileStoneResponse)
    {

      return mileStoneProgramService.createSession(merchant,dsMileStoneResponse);
    }

    @GetMapping(value = "/dashboardDetails")
    public ApiResponse<MileStoneDashboardDetails> dashboardDetails(
            @RequestAttribute BasicDetailsDto merchant
    ) {
        return mileStoneProgramService.dashboardDetails(merchant);
    }

    @PostMapping(value="/loan/Offer")
    public ApiResponse<?> checkLoanOffer(
            @RequestAttribute BasicDetailsDto merchant,
                                                 @RequestBody MileStoneOfferRequest mileStoneOfferRequest)
    {
        return mileStoneProgramService.milestoneOffer(merchant,mileStoneOfferRequest);
    }


    @PostMapping(value = "/claim/reward")
    public ApiResponse<?> claimReward(
            @RequestAttribute BasicDetailsDto merchant,
            @RequestParam(name = "rewardName") String rewardName,
            @RequestParam(name = "rewardClaimedStatus") Boolean rewardClaimedStatus) {

        return mileStoneProgramService.claimReward(merchant, rewardName, rewardClaimedStatus);
    }


    @GetMapping(value = "/program-details")
    public ApiResponse<Object>programAssignment(@RequestAttribute BasicDetailsDto merchant)
    {
         return mileStoneProgramService.programDetails(merchant);
    }


    @GetMapping(value = "/rte-eligibility-check")
    public ApiResponse<Object>checkRteEligibility(@RequestParam(required = true) Long merchantId) {
        log.info("merchant id is: {}", merchantId);
        return mileStoneProgramService.checkRteEligibility(merchantId);
    }

    @GetMapping(value = "/update-cashback-data")
    public ApiResponse<?> updatePageView(@RequestAttribute BasicDetailsDto merchant, @RequestParam(required = false) String cashBackEarned) {
        log.info("Updating pageViewed data for {}", merchant.getId());
        return mileStoneProgramService.updatePageViewData(merchant.getId(), cashBackEarned);
    }

    @GetMapping(value = "/rte-loan-eligibility-check")
    public ResponseEntity<ApiResponse<RteLoanEligibilityResponse>> checkRteLoanEligibility(@RequestParam Long merchantId) {
        log.info("computing rte and loan eligibility for merchant id: {}", merchantId);
        try {
            RteLoanEligibilityResponse rteLoanEligibilityResponse = loanAndRTEEligibilityComputeService.computeRteAndLoanEligibility(merchantId);
            log.info("rte and loan eligibility successful for merchant id: {}", merchantId);
            ApiResponse<RteLoanEligibilityResponse> apiResponse = new ApiResponse<>(rteLoanEligibilityResponse);
            return ResponseEntity.ok(apiResponse);
        }catch (CustomLendingException exception){
            log.error("CustomLendingException occurred while computing rte and loan eligibility for merchant id {}: {}", merchantId, exception.getMessage());
            ApiResponse<RteLoanEligibilityResponse> apiResponse = new ApiResponse<>(false, exception.getMessage());
            return ResponseEntity.status(exception.getStatus()).body(apiResponse);
        } catch (Exception exception){
            log.error("Error occurred while computing rte and loan eligibility for merchant id {}: {}", merchantId, exception.getMessage());
            ApiResponse<RteLoanEligibilityResponse> apiResponse = new ApiResponse<>(false, "Something went wrong");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(apiResponse);
        }

    }


    @GetMapping(value = "/evictCache")
    public void evictCache(@RequestParam Long merchantId)
    {
        mileStoneProgramService.evictCache(merchantId);
    }
}
