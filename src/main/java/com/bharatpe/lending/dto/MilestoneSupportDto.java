package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Data
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MilestoneSupportDto {
    private Boolean milestoneData;
    private MileStoneEligibilityResponseDto mileStoneEligibility;
    private Date programStartDate;
    private Date merchantOnboardingDate;
    private Boolean rewardStatus;
    private String rewardName;
    private Date claimedDate;

    private DSMileStoneResponse mileStoneResponse;
    private DSMileStoneAchievementResponse achievementResponse;
    private Long merchantId;
}

