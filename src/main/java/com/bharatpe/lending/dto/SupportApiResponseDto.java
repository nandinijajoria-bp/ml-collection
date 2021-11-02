package com.bharatpe.lending.dto;

import lombok.Data;

import javax.persistence.criteria.CriteriaBuilder;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Data
public class SupportApiResponseDto {
    Long merchantId;
    String applicationStage;
    //experian
    Boolean experian;
    Boolean eligible;
    String ineligibleType;
    Long reapplyTime;
    Integer pincode;
    //application
    String priority;
    String applicationStatus;
    Integer remainingTat;
    Integer tat;
    String rejectedStage;
    Boolean applied;
    Boolean tatBreached;
    Boolean fiRequired;
    Boolean eligibleToApplyAgain;
    //active loan
    Boolean activeLoan;
    Integer dpd;
    //closed loan;
    Boolean eligibleForRepeat;
    Boolean pfRefunded;
    Date closingDate;
    List<String> nachableBank = Arrays.asList();
}
