package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;

public interface IStageDataService<T> {

    LendingStateDTO<T> fetchScopedData(ScopeDataArgs scopeDataArgs);
    LendingStateDTO<T> processCurrentStage(ScopeDataArgs scopeDataArgs);
}
