package com.bharatpe.lending.loanV3.revamp.stateManager;

import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;

public interface IRenderStateViaScope {

    LendingStateDTO<?> populateNextLendingState(ScopeDataArgs scopeDataArgs);
    LendingStateDTO<?> fetchLendingStateData(ScopeDataArgs scopeDataArgs);
}
