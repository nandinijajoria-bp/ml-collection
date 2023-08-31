package com.bharatpe.lending.loanV3.revamp.dto;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import lombok.Builder;

@Builder
public class StateRenderArgs {
    public LendingViewStates scope;
    public LoanDetailsV3Request loanDetailsV3Request;

    public BasicDetailsDto merchant;
}
