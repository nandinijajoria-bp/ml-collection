package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ReferenceStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

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

    @Autowired
    private LoanUtil loanUtil;

    @Autowired
    private LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Override
    public LendingStateDTO<ReferenceStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<ReferenceStateDTO> lendingStateDTO = fetchScopedData(scopeDataArgs);
        if(LoanType.TOPUP.name().equalsIgnoreCase(lendingStateDTO.getData().getLoanType())) {
            //next page for topup is set in fetchScopeData call
            return lendingStateDTO;
        }

        lendingStateDTO.setLendingViewStates(LendingViewStates.AGREEMENT_PAGE);
        return lendingStateDTO;
    }

    @Override
    public LendingStateDTO<ReferenceStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        ReferenceStateDTO referenceStateDTO = new ReferenceStateDTO();
        LendingViewStates nextLendingViewState = LendingViewStates.REFERENCE_PAGE;
        try {
            referenceStateDTO.setMerchantId(scopeDataArgs.getMerchant().getId());
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
                referenceStateDTO.setLoanType(lendingApplication.getLoanType());
            }

            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(scopeDataArgs.getApplicationId());

            if(ObjectUtils.isEmpty(lendingApplicationDetails)) {
                log.info("Lending Application Details not found for applicationId: {}", lendingApplication.getId());
                throw new LoanDetailsException(LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorCode(), LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorMessage());
            }
            referenceStateDTO.setIsAadhaarAddressVerified(!ObjectUtils.isEmpty(lendingApplicationDetails.getCurrentAddressSameAsPermanentAddress()));
            referenceStateDTO.setLoanPurpose(lendingApplication.getLender().equalsIgnoreCase(Lender.PIRAMAL.name()) && ObjectUtils.isEmpty(lendingApplicationDetails.getLoanPurpose()));

            if(!ObjectUtils.isEmpty(scopeDataArgs.getMerchant())) {
                referenceStateDTO.setMerchantName(loanUtil.getBeneficiaryName(scopeDataArgs.getMerchant().getId()));
                referenceStateDTO.setMobile(scopeDataArgs.getMerchant().getMobile());
            }
            loanDetailsV3Service.saveApplicationViewState(null, scopeDataArgs.getApplicationId(), LendingViewStates.REFERENCE_PAGE);

            if(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())){
                nextLendingViewState = loanUtil.getNextLendingViewStateForTopup(lendingApplicationDetails, lendingApplication);
            }

        } catch (Exception e) {
            log.error("error in getting reference stage data for {} : {}, {}", scopeDataArgs.getMerchant().getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            throw new LoanDetailsException(LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorCode(),LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorMessage());
        }
        return new LendingStateDTO<>(referenceStateDTO , nextLendingViewState, LendingViewStates.REFERENCE_PAGE);
    }
}
