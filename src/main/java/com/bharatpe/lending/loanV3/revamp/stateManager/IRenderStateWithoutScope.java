package com.bharatpe.lending.loanV3.revamp.stateManager;

import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.LoanDetailsV3Response;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;

public interface IRenderStateWithoutScope {
    LoanDetailsV3Response fetchLendingStateData(ScopeDataArgs scopeDataArgs);
}
