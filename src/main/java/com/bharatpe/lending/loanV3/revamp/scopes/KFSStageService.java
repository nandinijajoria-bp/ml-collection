package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.loanV2.dto.LoanApplicationDetails;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.enums.ApplicationStatus;
import com.bharatpe.lending.loanV3.revamp.dto.KFSStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.LoanApplicationDetailsV3;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.services.LendingApplicationServiceV3;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.bharatpe.lending.service.LendingApplicationService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

@Component
@Slf4j
public class KFSStageService implements IStageDataService<KFSStateDTO> {

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    LendingApplicationServiceV3 lendingApplicationServiceV3;

    @Autowired
    LoanDetailsV3Service loanDetailsV3Service;

    @Autowired
    private LoanDashboardService loanDashboardService;

    @Override
    public LendingStateDTO<KFSStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<KFSStateDTO> lendingStateDTO = fetchScopedData(scopeDataArgs);
        lendingStateDTO.setLendingViewStates(LendingViewStates.ENACH_PAGE);
        if(!ObjectUtils.isEmpty(lendingStateDTO) && !ObjectUtils.isEmpty(lendingStateDTO.getData())){
            if((Objects.nonNull(lendingStateDTO.getData().getLoanApplication()) &&
                    Objects.nonNull(lendingStateDTO.getData().getLoanApplication().getEnachDone()) &&
                    lendingStateDTO.getData().getLoanApplication().getEnachDone()) ||
                    (Objects.nonNull(lendingStateDTO.getData().getTopupLoanApplication()) &&
                            Objects.nonNull(lendingStateDTO.getData().getTopupLoanApplication().getEnachDone()) &&
                            lendingStateDTO.getData().getTopupLoanApplication().getEnachDone())){
                lendingStateDTO.setLendingViewStates(LendingViewStates.APPLICATION_STATUS_PAGE);
            }
        }
        loanDetailsV3Service.saveApplicationViewState(null, scopeDataArgs.getApplicationId(), LendingViewStates.KEY_FACTOR_STATEMENT_PAGE);
        return lendingStateDTO;
    }

    @Override
    public LendingStateDTO<KFSStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        KFSStateDTO kfsStageResponseV3 = new KFSStateDTO();
        try {
            kfsStageResponseV3.setMobile(scopeDataArgs.getMerchant().getMobile());
            kfsStageResponseV3.setRepeatLoan(loanUtil.isRepeatLoan(scopeDataArgs.getMerchant().getId()));

            LendingApplication lendingApplication = lendingApplicationServiceV3.getLendingApplication(scopeDataArgs.getApplicationId(), scopeDataArgs.getMerchant().getId());
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("Application not found for {}", scopeDataArgs.getMerchant().getId());
                throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorMessage());
            }
            scopeDataArgs.setApplicationId(lendingApplication.getId());
            if("TOPUP".equalsIgnoreCase(lendingApplication.getLoanType())){
                LendingApplication topupApplication = lendingApplicationDao.findOpenTopUpApplication(scopeDataArgs.getMerchant().getId(), "TOPUP");
                if(!ObjectUtils.isEmpty(topupApplication) && !"rejected".equals(topupApplication.getStatus()) && !"DISBURSED".equalsIgnoreCase(topupApplication.getLoanDisbursalStatus())){
                    LoanApplicationDetailsV3 topupApplicationDetails = setApplicationDetails(topupApplication);
                    kfsStageResponseV3.setTopupLoanApplication(topupApplicationDetails);
                }
                return new LendingStateDTO<>(kfsStageResponseV3 , LendingViewStates.KEY_FACTOR_STATEMENT_PAGE, LendingViewStates.KEY_FACTOR_STATEMENT_PAGE);
            }
            LoanApplicationDetailsV3 loanApplicationDetails = setApplicationDetails(lendingApplication);
            kfsStageResponseV3.setLoanApplication(loanApplicationDetails);
            return new LendingStateDTO<>(kfsStageResponseV3 , LendingViewStates.KEY_FACTOR_STATEMENT_PAGE, LendingViewStates.KEY_FACTOR_STATEMENT_PAGE);
        } catch (Exception e) {
            log.error("Error while fetching KFS stage data for {}, {}, {}", scopeDataArgs.getMerchant().getMobile(),
                    e.getMessage(), Arrays.asList(e.getStackTrace()));
            throw new LoanDetailsException(LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorCode(),LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorMessage());
        }
    }

    public LoanApplicationDetailsV3 setApplicationDetails(LendingApplication openApplication) {
        LoanApplicationDetailsV3 applicationDetails = new LoanApplicationDetailsV3();
        applicationDetails.setApplicationId(openApplication.getId());
        applicationDetails.setLoanAmount(openApplication.getLoanAmount());

        if (!loanUtil.isEnachBank(openApplication.getMerchantId())) {
            log.info("bank not nachable for {}", openApplication.getId());
            throw new LoanDetailsException(LoanDetailExceptionEnum.NON_NACHABLE_BANK.getErrorCode(),LoanDetailExceptionEnum.NON_NACHABLE_BANK.getErrorMessage());
        }
        if (easyLoanUtil.isDummyMerchant(openApplication.getMerchantId()) || loanUtil.isEnachDone(openApplication.getMerchantId(), openApplication.getId()) ||
                loanUtil.isEligibleForNachSkip(openApplication, openApplication.getLender())) {
            if(ObjectUtils.isEmpty(openApplication.getNachStatus())){
                loanDashboardService.deleteLoanDashboardCache(openApplication.getMerchantId());
            }
            openApplication.setNachStatus("APPROVED");
            openApplication.setNachType("ENACH");
            openApplication.setNachLender(loanUtil.enachServiceLenderMapper(openApplication.getLender()));
            lendingApplicationDao.save(openApplication);
            loanDetailsV3Service.saveApplicationViewState(null, openApplication.getId(), LendingViewStates.APPLICATION_STATUS_PAGE);
        }
        applicationDetails.setSkipEnach(loanUtil.isEligibleForNachSkip(openApplication, openApplication.getLender()));
        if (ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(openApplication.getStatus())) {
            applicationDetails.setEnachDone("APPROVED".equalsIgnoreCase(openApplication.getNachStatus()));
        }
        if("TOPUP".equalsIgnoreCase(openApplication.getLoanType()) && ApplicationStatus.DRAFT.name().equalsIgnoreCase(openApplication.getStatus())){
            applicationDetails.setEnachDone("APPROVED".equalsIgnoreCase(openApplication.getNachStatus()));
        }
        return applicationDetails;
    }
}
