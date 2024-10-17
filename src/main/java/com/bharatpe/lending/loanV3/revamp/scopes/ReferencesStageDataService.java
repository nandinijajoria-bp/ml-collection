package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.revamp.dto.AgreementStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ReferenceStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.lang.ref.Reference;
import java.util.Arrays;

@Component
@Slf4j
public class ReferencesStageDataService implements IStageDataService<ReferenceStateDTO>{

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    private LoanDetailsV3Service loanDetailsV3Service;

    @Autowired
    private LendingApplicationDao lendingApplicationDao;

    @Override
    public LendingStateDTO<ReferenceStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<ReferenceStateDTO> lendingStateDTO = fetchScopedData(scopeDataArgs);
        lendingStateDTO.setLendingViewStates(LendingViewStates.AGREEMENT_PAGE);

        return lendingStateDTO;
    }

    @Override
    public LendingStateDTO<ReferenceStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        ReferenceStateDTO referenceStateDTO = new ReferenceStateDTO();
        try {
            referenceStateDTO.setDummyMerchant(easyLoanUtil.isDummyMerchant(scopeDataArgs.getMerchant().getId()));
            if (ObjectUtils.isEmpty(scopeDataArgs.getApplicationId())) {
                log.info("Application not found for {}", scopeDataArgs.getMerchant().getId());
                throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorMessage());
            }
            log.info("scope application id {}", scopeDataArgs.getApplicationId());
            LendingApplication lendingApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(scopeDataArgs.getMerchant().getId());
            log.info("lendingApplication {} and status {}", lendingApplication, lendingApplication.getStatus());
            if (!ObjectUtils.isEmpty(lendingApplication) && !ObjectUtils.isEmpty(lendingApplication.getStatus())) {
                referenceStateDTO.setApplicationStatus(lendingApplication.getStatus());
                referenceStateDTO.setLender(lendingApplication.getLender());
            }
            if(!ObjectUtils.isEmpty(scopeDataArgs.getMerchant())) {
                referenceStateDTO.setMerchantName(scopeDataArgs.getMerchant().getName());
                referenceStateDTO.setMobile(scopeDataArgs.getMerchant().getMobile());
            }

            loanDetailsV3Service.saveApplicationViewState(null, scopeDataArgs.getApplicationId(), LendingViewStates.REFERENCE_PAGE);
        } catch (Exception e) {
            log.error("error in getting reference stage data for {} : {}, {}", scopeDataArgs.getMerchant().getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            throw new LoanDetailsException(LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorCode(),LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorMessage());
        }
        return new LendingStateDTO<>(referenceStateDTO , LendingViewStates.REFERENCE_PAGE, LendingViewStates.REFERENCE_PAGE);
    }
}
