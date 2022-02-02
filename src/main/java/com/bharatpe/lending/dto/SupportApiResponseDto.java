package com.bharatpe.lending.dto;

import com.bharatpe.common.entities.BankList;
import com.bharatpe.common.entities.LendingPaymentSchedule;
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
    Boolean applied;
    Boolean tatBreached;
    Boolean fiRequired;
    Boolean eligibleToApplyAgain;
    Boolean enachDone;
    String currentStage;
    String loanType;
    Date agreementAt;
    Double processingFee;
    String processingType;
    String applicationRejectReason;
    String processingStage;
    List<ApplicationDTO> applicationJourney;
    //active loan
    Boolean activeLoan;
    Integer dpd;
    Boolean eligibleForTopUp;
    //closed loan;
    Boolean eligibleForRepeat;
    Boolean pfRefunded;
    Date closingDate;

    //additional Params
    List<LendingPaymentSchedule> closedLoans;
    List<BankList> nachableBanks;
}
