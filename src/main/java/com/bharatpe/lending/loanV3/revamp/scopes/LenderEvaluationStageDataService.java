package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.dto.LenderEvaluationStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ReferenceStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.util.LoanUtilV3;
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
    LoanUtilV3 loanUtilV3;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Override
    public LendingStateDTO<LenderEvaluationStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<LenderEvaluationStateDTO> lendingStateDTO = fetchScopedData(scopeDataArgs);
        if(loanUtilV3.isPreapprovedRepeatLoan(scopeDataArgs.getApplicationId())){
            lendingStateDTO.setLendingViewStates(LendingViewStates.AGREEMENT_PAGE);
        }
        else{
            lendingStateDTO.setLendingViewStates(LendingViewStates.REFERENCE_PAGE);
        }
        LendingApplication lendingApplication = lendingApplicationDao.findById(scopeDataArgs.getApplicationId()).orElse(null);
        if(ObjectUtils.isEmpty(lendingApplication)) {
            return lendingStateDTO;
        }
        if(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()) && Lender.ABFL.name().equalsIgnoreCase(lendingApplication.getLender())) {
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findByApplicationIdAndLender(lendingApplication.getId(), Lender.ABFL.name());
            if(!ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                if(LenderAssociationStatus.BRE_FAILED.name().equalsIgnoreCase(lendingApplicationLenderDetails.getBreStatus())) {
                    log.info("marking application rejected and lendingApplicationLenderDetails INACTIVE as BRE_FAILED for applicationId: {}", lendingApplication.getId());
                    lendingApplication.setStatus("rejected");
                    lendingApplicationDao.save(lendingApplication);
                    lendingApplicationLenderDetails.setStatus(Status.INACTIVE.name());
                    lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                    lendingStateDTO.setLendingViewStates(LendingViewStates.LENDER_TOPUP_REJECTED);
                } else if(LenderAssociationStatus.BRE_RETRY.name().equalsIgnoreCase(lendingApplicationLenderDetails.getBreStatus())){
                    lendingStateDTO.getData().setIsRetryable(Boolean.TRUE);
                    lendingStateDTO.setLendingViewStates(LendingViewStates.LENDER_EVALUATION_PAGE);
                } else if (LenderAssociationStatus.KYC_FAILED.name().equalsIgnoreCase(lendingApplicationLenderDetails.getKycStatus())) {
                    log.info("marking application rejected and lendingApplicationLenderDetails INACTIVE as KYC_FAILED for applicationId: {}", lendingApplication.getId());
                    lendingApplication.setStatus("rejected");
                    lendingApplicationDao.save(lendingApplication);
                    lendingApplicationLenderDetails.setStatus(Status.INACTIVE.name());
                    lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                    lendingStateDTO.setLendingViewStates(LendingViewStates.LENDER_TOPUP_REJECTED);
                } else if (LenderAssociationStatus.KYC_RETRY.name().equalsIgnoreCase(lendingApplicationLenderDetails.getKycStatus())) {
                    lendingStateDTO.setLendingViewStates(LendingViewStates.KYC_PAGE);
                }
                else if (LenderAssociationStatus.KYC_COMPLETED.name().equalsIgnoreCase(lendingApplicationLenderDetails.getKycStatus())) {
                    lendingStateDTO.setLendingViewStates(LendingViewStates.ENACH_PAGE);
                }

            }
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
}
