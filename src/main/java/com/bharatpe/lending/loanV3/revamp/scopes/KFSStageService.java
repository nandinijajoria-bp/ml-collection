package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.lending.common.dao.AutoPayUPIDao;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.enums.MandateType;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.revamp.dto.KFSStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.LoanApplicationDetailsV3;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.services.EnachStageHelper;
import com.bharatpe.lending.loanV3.revamp.services.LendingApplicationServiceV3;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.bharatpe.lending.util.LoanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class KFSStageService implements IStageDataService<KFSStateDTO> {

    private final LoanUtil loanUtil;
    private final LendingApplicationDao lendingApplicationDao;
    private final LendingApplicationServiceV3 lendingApplicationServiceV3;
    private final LoanDetailsV3Service loanDetailsV3Service;
    private final LendingApplicationDetailsDao lendingApplicationDetailsDao;
    private final AutoPayUPIDao autoPayUPIDao;
    private final UpiAutoPayStageHelper upiAutoPayStageHelper;
    private final EnachStageHelper enachStageHelper;
    private final KfsStageHelper kfsStageHelper;

    private final List<MandateType> upiAutoPayEligibleMandateTypes = Arrays.asList(MandateType.DIGIO_UPI, MandateType.UPIAUTOPAY, MandateType.BOTH);

    @Override
    public LendingStateDTO<KFSStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<KFSStateDTO> lendingStateDTO = fetchScopedData(scopeDataArgs);

        KFSStateDTO data = lendingStateDTO.getData();
        lendingStateDTO.setLendingViewStates(kfsStageHelper.getNextViewState(
                data.getMerchantId(),scopeDataArgs.getApplicationId(),data.getLender(),data.getMandateType(),
                data.isNachDone(),data.isUpiAutoPayDone(), Objects.nonNull(data.getTopupLoanApplication())
        ));
        loanDetailsV3Service.saveApplicationViewState(null, scopeDataArgs.getApplicationId(), LendingViewStates.KEY_FACTOR_STATEMENT_PAGE);
        return lendingStateDTO;
    }

    @Override
    public LendingStateDTO<KFSStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        KFSStateDTO kfsStageResponseV3 = new KFSStateDTO();
        try {
            kfsStageResponseV3.setMobile(scopeDataArgs.getMerchant().getMobile());
            kfsStageResponseV3.setRepeatLoan(loanUtil.isRepeatLoan(scopeDataArgs.getMerchant().getId()));
            kfsStageResponseV3.setMerchantId(scopeDataArgs.getMerchant().getId());

            LendingApplication lendingApplication = lendingApplicationServiceV3.getLendingApplication(scopeDataArgs.getApplicationId(), scopeDataArgs.getMerchant().getId());
            log.info("fetched application id {} for merchantId {}",lendingApplication.getId(),scopeDataArgs.getMerchant().getId());
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("Application not found for {}", scopeDataArgs.getMerchant().getId());
                throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorMessage());
            }
            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
            if(ObjectUtils.isEmpty(lendingApplicationDetails)) {
                log.info("Lending Application Details not found for {}", lendingApplication.getId());
                throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_DETAILS_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_DETAILS_NOT_FOUND.getErrorMessage());
            }
            loanUtil.updateMandateColumnsInLAD(lendingApplicationDetails, lendingApplication.getLender(), lendingApplication.getLoanAmount());
            kfsStageResponseV3.setMandateType(lendingApplicationDetails.getMandateType());
            log.info("Kfs stage response for application id {} is {}", lendingApplication.getId(), kfsStageResponseV3);

            scopeDataArgs.setApplicationId(lendingApplication.getId());
            kfsStageResponseV3.setLender(lendingApplication.getLender());
            if(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())){
                LendingApplication topupApplication = lendingApplicationDao.findOpenTopUpApplication(scopeDataArgs.getMerchant().getId(), LoanType.TOPUP.name());
                if(!ObjectUtils.isEmpty(topupApplication) && !"rejected".equals(topupApplication.getStatus()) && !"DISBURSED".equalsIgnoreCase(topupApplication.getLoanDisbursalStatus())){
                    LoanApplicationDetailsV3 topupApplicationDetails = getApplicationDetails(topupApplication);
                    kfsStageResponseV3.setTopupLoanApplication(topupApplicationDetails);
                }
                return new LendingStateDTO<>(kfsStageResponseV3 , LendingViewStates.KEY_FACTOR_STATEMENT_PAGE, LendingViewStates.KEY_FACTOR_STATEMENT_PAGE);
            }

            LoanApplicationDetailsV3 loanApplicationDetails = getApplicationDetails(lendingApplication);
            kfsStageResponseV3.setLoanApplication(loanApplicationDetails);
            if(!LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())){
                updateMandateStatus(lendingApplicationDetails, kfsStageResponseV3, lendingApplication);
            }
            return new LendingStateDTO<>(kfsStageResponseV3 , LendingViewStates.KEY_FACTOR_STATEMENT_PAGE, LendingViewStates.KEY_FACTOR_STATEMENT_PAGE);
        } catch (Exception e) {
            log.error("Error while fetching KFS stage data for {}, {}, {}", scopeDataArgs.getMerchant().getMobile(),
                    e.getMessage(), Arrays.asList(e.getStackTrace()));
            throw new LoanDetailsException(LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorCode(),LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorMessage());
        }
    }

    private void updateMandateStatus(LendingApplicationDetails lendingApplicationDetails, KFSStateDTO kfsStageResponseV3, LendingApplication lendingApplication) {
        if(MandateType.BOTH.equals(lendingApplicationDetails.getMandateType())){
            kfsStageResponseV3.setUpiAutoPayDone(upiAutoPayStageHelper.checkSkipEligibilityAndUpdateUpiAutoPayStatus(lendingApplication));
            kfsStageResponseV3.setNachDone(enachStageHelper.checkNachSkipEligibilityAndUpdateStatus(lendingApplication, lendingApplicationDetails));
        }else if(MandateType.UPIAUTOPAY.equals(lendingApplicationDetails.getMandateType())){
            kfsStageResponseV3.setUpiAutoPayDone(upiAutoPayStageHelper.checkSkipEligibilityAndUpdateUpiAutoPayStatus(lendingApplication));
        }else if(MandateType.DIGIO_UPI.equals(lendingApplicationDetails.getMandateType())){
            // TODO write digio upi skip case
        }else {
            kfsStageResponseV3.setNachDone(enachStageHelper.checkNachSkipEligibilityAndUpdateStatus(lendingApplication, lendingApplicationDetails));
        }
    }

    private LoanApplicationDetailsV3 getApplicationDetails(LendingApplication openApplication){
        LoanApplicationDetailsV3 applicationDetails = new LoanApplicationDetailsV3();
        applicationDetails.setApplicationId(openApplication.getId());
        applicationDetails.setLoanAmount(openApplication.getLoanAmount());
        return applicationDetails;
    }

}
