package com.bharatpe.lending.controller;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.service.MileStoneProgramService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/milestone")
@Slf4j
public class MileStoneProgramController {

    @Autowired
    MileStoneProgramService mileStoneProgramService;

    @GetMapping(value = "/eligibility")
    public ApiResponse<MileStoneEligibilityResponseDto> computeEligibility(
            @RequestAttribute BasicDetailsDto merchant
    ) {
        return mileStoneProgramService.checkEligibility(merchant);
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



    @GetMapping(value = "/evictCache")
    public void evictCache(@RequestParam Long merchantId)
    {
        mileStoneProgramService.evictCache(merchantId);
    }
}
