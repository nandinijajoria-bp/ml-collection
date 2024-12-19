package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.dto.TopupRejectionStateDTO;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;

@Service
@Slf4j
public class TopupRejectionStageDataService implements IStageDataService{
    @Override
    public LendingStateDTO<TopupRejectionStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<TopupRejectionStateDTO> lendingStateDTO = fetchScopedData(scopeDataArgs);
        return lendingStateDTO;
    }

    @Override
    public LendingStateDTO<TopupRejectionStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        TopupRejectionStateDTO topupRejectionStateDTO = new TopupRejectionStateDTO();
        topupRejectionStateDTO.setMerchantId(scopeDataArgs.getMerchant().getId());
        try {
            if(ObjectUtils.isEmpty(scopeDataArgs.getApplicationId())){
                throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_ID_MISSING.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_ID_MISSING.getErrorMessage());
            }
            return new LendingStateDTO<>(topupRejectionStateDTO , LendingViewStates.LENDER_TOPUP_REJECTED, LendingViewStates.LENDER_TOPUP_REJECTED);
        } catch (Exception e) {
            log.error("error in getting topup rejection stage data for {} : {}, {}", scopeDataArgs.getMerchant().getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            throw new LoanDetailsException(LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorCode(),LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorMessage());
        }
    }
}
