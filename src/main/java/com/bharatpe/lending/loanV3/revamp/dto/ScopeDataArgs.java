package com.bharatpe.lending.loanV3.revamp.dto;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ScopeDataArgs {
    LendingViewStates currentState;
    LoanDetailsV3Request loanDetailsV3Request;

    public BasicDetailsDto merchant;

    LendingStateDTO lendingStateDTOForCurrPage;
    LendingStateDTO lendingStateDTOForNextPage;

    LendingApplication openApplication;

    EligibilityStateDTO eligibilityStateDTO;

    String token;

    Long applicationId;
}
