package com.bharatpe.lending.loanV3.revamp.stateManager;

import com.bharatpe.lending.loanV3.revamp.config.StageServiceFactory;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.scopes.IStageDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RenderStateViaScope implements IRenderStateViaScope {

    @Autowired
    StageServiceFactory stageServiceFactory;

    @Override
    public LendingStateDTO<?> populateNextLendingState(ScopeDataArgs scopeDataArgs) {
        log.info("scopeDataArgs for {} : {}",scopeDataArgs.getMerchant().getId(), scopeDataArgs);
        IStageDataService iStageDataService = stageServiceFactory.getStageService(scopeDataArgs.getCurrentState());
        LendingStateDTO lendingStateDTO = iStageDataService.processCurrentStage(scopeDataArgs);
        log.info("lendingStateDto for {} : {}", scopeDataArgs.getMerchant().getId(), lendingStateDTO);
        scopeDataArgs.setLendingStateDTOForCurrPage(lendingStateDTO);
        return lendingStateDTO;
    }

    @Override
    public LendingStateDTO<?> fetchLendingStateData(ScopeDataArgs scopeDataArgs) {
        log.info("scopeDataArgs : {}", scopeDataArgs);
        IStageDataService iStageDataService = stageServiceFactory.getStageService(scopeDataArgs.getCurrentState());
        log.info("iStageDataService : {}", iStageDataService);
        LendingStateDTO lendingStateDTO = iStageDataService.fetchScopedData(scopeDataArgs);
        scopeDataArgs.setLendingStateDTOForCurrPage(lendingStateDTO);
        return lendingStateDTO;
    }
}
