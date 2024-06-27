package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.loanV3.revamp.dto.LenderEvaluationStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.services.LendingApplicationServiceV3;
import com.bharatpe.lending.loanV3.revamp.util.LoanUtilV3;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;

@Component
@Slf4j
public class LenderEvaluationStageDataService implements IStageDataService<LenderEvaluationStateDTO>{

    @Autowired
    LoanUtilV3 loanUtilV3;

    @Autowired
    LendingApplicationServiceV3 lendingApplicationServiceV3;

    @Override
    public LendingStateDTO<LenderEvaluationStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<LenderEvaluationStateDTO> lendingStateDTO = fetchScopedData(scopeDataArgs);
        if(loanUtilV3.isPreapprovedRepeatLoan(scopeDataArgs.getApplicationId())){
            lendingStateDTO.setLendingViewStates(LendingViewStates.AGREEMENT_PAGE);
        }
        if(loanUtilV3.isReferenceNotRequired(scopeDataArgs.getApplicationId())) {
            lendingStateDTO.setLendingViewStates(LendingViewStates.AGREEMENT_PAGE);
        }
        else{
            lendingStateDTO.setLendingViewStates(LendingViewStates.REFERENCE_PAGE);
        }
        return lendingStateDTO;
    }

    @Override
    public LendingStateDTO<LenderEvaluationStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        LenderEvaluationStateDTO lenderEvaluationStateDTO = new LenderEvaluationStateDTO();
        try {
            // dont make db call, just return next page
            if(ObjectUtils.isEmpty(scopeDataArgs.getApplicationId())){
                throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_ID_MISSING.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_ID_MISSING.getErrorMessage());
            }
            LendingApplication lendingApplication = lendingApplicationServiceV3.getLendingApplication(scopeDataArgs.getApplicationId(), scopeDataArgs.getMerchant().getId());
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("Application not found for {}", scopeDataArgs.getMerchant().getId());
                throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorMessage());
            }
            lenderEvaluationStateDTO.setLender(lendingApplication.getLender());
            return new LendingStateDTO<>(lenderEvaluationStateDTO , LendingViewStates.LENDER_EVALUATION_PAGE, LendingViewStates.LENDER_EVALUATION_PAGE);
        } catch (Exception e) {
            log.error("error in getting reference stage data for {} : {}, {}", scopeDataArgs.getMerchant().getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            throw new LoanDetailsException(LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorCode(),LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorMessage());
        }
    }
}
