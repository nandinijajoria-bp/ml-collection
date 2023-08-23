package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.dto.LenderEvaluationStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ReferenceStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

@Component
@Slf4j
public class LenderEvaluationStageDataService implements IStageDataService<LenderEvaluationStateDTO>{

    @Autowired
    private LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Override
    public LendingStateDTO<LenderEvaluationStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<LenderEvaluationStateDTO> lendingStateDTO = fetchScopedData(scopeDataArgs);
        if(isPreapprovedRepeatLoan(scopeDataArgs.getApplicationId())){
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
            return new LendingStateDTO<>(lenderEvaluationStateDTO , LendingViewStates.LENDER_EVALUATION_PAGE, LendingViewStates.LENDER_EVALUATION_PAGE);
        } catch (Exception e) {
            log.error("error in getting reference stage data for {} : {}, {}", scopeDataArgs.getMerchant().getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            throw new LoanDetailsException(LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorCode(),LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorMessage());
        }
    }

    private boolean isPreapprovedRepeatLoan(Long applicationId){
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(applicationId);
        if(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot))return false;
        String pilotIdentifier = lendingRiskVariablesSnapshot.getPilotIdentifier();
        if(!ObjectUtils.isEmpty(pilotIdentifier) && pilotIdentifier.contains(LoanDetailsConstant.PREAPPROVED_REPEAT_LOAN_IDENTIFIER)){
            return true;
        }
        return false;
    }
}
