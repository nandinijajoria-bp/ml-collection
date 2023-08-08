package com.bharatpe.lending.loanV3.revamp.services;


import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV3.revamp.config.StageServiceFactory;
import com.bharatpe.lending.loanV3.revamp.dto.*;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.scopes.IStageDataService;
import com.bharatpe.lending.loanV3.revamp.stateManager.IRenderStateViaScope;
import com.bharatpe.lending.loanV3.revamp.stateManager.IRenderStateWithoutScope;
import com.bharatpe.lending.loanV3.revamp.stateManager.RenderStateViaScope;
import com.bharatpe.lending.loanV3.revamp.stateManager.RenderStateWithoutScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
public class LoanDetailsV3Service {

    @Autowired
    RenderStateViaScope renderStateViaScope;

    @Autowired
    RenderStateWithoutScope renderStateWithoutScope;

    @Autowired
    private LendingApplicationDetailsDao lendingApplicationDetailsDao;

    public LoanDetailsV3Response getLoanDetails(LoanDetailsV3Request request, BasicDetailsDto merchant, String token)
    {
        log.info("LoanDetailsV3Request for {} : {}", merchant.getId(), request);
        LoanDetailsV3Response loanDetailsV3Response = new LoanDetailsV3Response();
        if (null != request.getScope()) {
            ScopeDataArgs scopeDataArgs = ScopeDataArgs.builder()
                    .currentState(LendingViewStates.valueOf(request.getScope()))
                    .loanDetailsV3Request(request)
                    .merchant(merchant)
                    .token(token)
                    .applicationId(request.getApplicationId())
                    .build();
            renderStateViaScope.populateNextLendingState(scopeDataArgs);
            LoanDetailsV3Response.populateResponseForRequestWithScope(scopeDataArgs.getLendingStateDTOForCurrPage(), loanDetailsV3Response);
            log.info("LoanDetailsV3Response for {} : {} ", merchant.getId(), loanDetailsV3Response);
            return loanDetailsV3Response;
        }
        throw new LoanDetailsException(LoanDetailExceptionEnum.NO_SCOPE_PROVIDED.getErrorCode(),LoanDetailExceptionEnum.NO_SCOPE_PROVIDED.getErrorMessage());
    }

    public LoanDetailsV3Response getLoanDetailsWithoutScope(BasicDetailsDto merchant, String scope, Long applicationId){
        if (null == scope) {
            ScopeDataArgs scopeDataArgs = ScopeDataArgs.builder()
                    .loanDetailsV3Request(new LoanDetailsV3Request())
                    .merchant(merchant)
                    .applicationId(applicationId)
                    .build();
           return renderStateWithoutScope.fetchLendingStateData(scopeDataArgs);
        } else {
            LoanDetailsV3Response loanDetailsV3Response = new LoanDetailsV3Response();
            ScopeDataArgs scopeDataArgs = ScopeDataArgs.builder()
                    .currentState(LendingViewStates.valueOf(scope))
                    .loanDetailsV3Request(new LoanDetailsV3Request())
                    .merchant(merchant)
                    .build();
            renderStateViaScope.fetchLendingStateData(scopeDataArgs);
           return LoanDetailsV3Response.populateResponseForRequestWithScope(scopeDataArgs.getLendingStateDTOForCurrPage(), loanDetailsV3Response);
        }
    }

    public void saveApplicationViewState(LendingApplicationDetails lendingApplicationDetails, Long applicationId, LendingViewStates nextState){
        try{
            if(Objects.isNull(nextState)){
                log.info("LendingViewState or applicationId is empty {}, {}", nextState.name(), applicationId);
            }
            LendingApplicationDetails lendingApplicationDetails1 = lendingApplicationDetails;
            if(ObjectUtils.isEmpty(lendingApplicationDetails1)){
                lendingApplicationDetails1 = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(applicationId);
                if(ObjectUtils.isEmpty(lendingApplicationDetails1)){
                    log.info("LendingApplicationDetails entry not found for {}", applicationId);
                    return;
                }
            }
            if(nextState.name().equalsIgnoreCase(lendingApplicationDetails1.getApplicationViewState()))return;

            lendingApplicationDetails1.setApplicationViewState(nextState.name());
            lendingApplicationDetailsDao.save(lendingApplicationDetails1);
        }
        catch (Exception e){
            log.error("Exception while updating application View state for {}, {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }
}
