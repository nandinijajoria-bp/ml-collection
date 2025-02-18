package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.AutoPayStatusEnum;
import com.bharatpe.lending.dao.AutoPayUPIDao;
import com.bharatpe.lending.dto.MandateUPIStatusResponse;
import com.bharatpe.lending.entity.AutoPayUPI;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.ApplicationStatus;
import com.bharatpe.lending.loanV3.revamp.dto.KFSStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.LoanApplicationDetailsV3;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.services.LendingApplicationServiceV3;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.bharatpe.lending.service.AutoPayUPIService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Objects;

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

    @Autowired
    AutoPayUPIDao autoPayUPIDao;

    @Autowired
    AutoPayUPIService autoPayUPIService;

    @Value("${enable.autopayupi.registration:false}")
    private Boolean enableAutopayUPIRegistration;

    @Value("${merchant.plugin.rollout.percent:0}")
    Integer merchantPluginRolloutPercent;

    @Override
    public LendingStateDTO<KFSStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<KFSStateDTO> lendingStateDTO = fetchScopedData(scopeDataArgs);

        if (Objects.nonNull(lendingStateDTO.getData().getUpiAutoPayEligible()) && lendingStateDTO.getData().getUpiAutoPayEligible()) {
            // we want the user to be on the kfs page until the autopayupi is successfully done
            if (!AutoPayStatusEnum.ACTIVE.name().equals(lendingStateDTO.getData().getUpiAutoPayMandateStatus())) {
                lendingStateDTO.setLendingViewStates(LendingViewStates.KEY_FACTOR_STATEMENT_PAGE);
                loanDetailsV3Service.saveApplicationViewState(null, scopeDataArgs.getApplicationId(), LendingViewStates.KEY_FACTOR_STATEMENT_PAGE);
                return lendingStateDTO;
            } else {
                lendingStateDTO.setLendingViewStates(LendingViewStates.ENACH_PAGE);
            }

        } else lendingStateDTO.setLendingViewStates(LendingViewStates.ENACH_PAGE);
        if(!ObjectUtils.isEmpty(lendingStateDTO) && !ObjectUtils.isEmpty(lendingStateDTO.getData())){
            if((Objects.nonNull(lendingStateDTO.getData().getLoanApplication()) &&
                    Objects.nonNull(lendingStateDTO.getData().getLoanApplication().getEnachDone()) &&
                    lendingStateDTO.getData().getLoanApplication().getEnachDone())
              ||

              (Objects.nonNull(lendingStateDTO.getData().getTopupLoanApplication()) &&
                            Objects.nonNull(lendingStateDTO.getData().getTopupLoanApplication().getEnachDone()) &&
                            lendingStateDTO.getData().getTopupLoanApplication().getEnachDone())) {
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
            kfsStageResponseV3.setMerchantId(scopeDataArgs.getMerchant().getId());

            LendingApplication lendingApplication = lendingApplicationServiceV3.getLendingApplication(scopeDataArgs.getApplicationId(), scopeDataArgs.getMerchant().getId());
            log.info("fetched application id {} for merchantId {}",lendingApplication,scopeDataArgs.getMerchant().getId());
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("Application not found for {}", scopeDataArgs.getMerchant().getId());
                throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorMessage());
            }
            if (enableAutopayUPIRegistration && loanUtil.isApplicationEligibleForAutoPayUpi(lendingApplication.getLender(), lendingApplication.getMerchantId(), lendingApplication.getLoanAmount()) && !loanUtil.checkIfUpiAutoPayNotRequired(lendingApplication)
           && easyLoanUtil.percentScaleUp(lendingApplication.getMerchantId(), merchantPluginRolloutPercent)) {
                log.info("setting autopay for application id {}", lendingApplication.getId());
                kfsStageResponseV3.setUpiAutoPayEligible(true);

                AutoPayUPI autoPayUPIExistingEntityMerchantId = autoPayUPIDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(lendingApplication.getMerchantId(),"ACTIVE");
//                if(!"REGULAR".equalsIgnoreCase( lendingApplication.getLoanType())){
                boolean autoPayEligible= nonRegularMandateRegistrationChecks(lendingApplication,autoPayUPIExistingEntityMerchantId);
                kfsStageResponseV3.setUpiAutoPayEligible(autoPayEligible);

                if("REGULAR".equalsIgnoreCase( lendingApplication.getLoanType()) && !autoPayEligible && autoPayUPIExistingEntityMerchantId != null){
                    autoPayUPIExistingEntityMerchantId.setApplicationId(lendingApplication.getId());
                    autoPayUPIDao.save(autoPayUPIExistingEntityMerchantId);
                }
                if("REGULAR".equalsIgnoreCase( lendingApplication.getLoanType()) && autoPayUPIExistingEntityMerchantId != null && autoPayEligible){
                    // revoke previous mandate
                    autoPayUPIService.revokeMandate(lendingApplication,autoPayUPIExistingEntityMerchantId);
                }
//                }
                log.info("autopay flag status {}",kfsStageResponseV3.getUpiAutoPayEligible());

                AutoPayUPI autoPayUPIExistingEntity = autoPayUPIDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId());
                log.info("autopay upi found for application id {} is {}",lendingApplication.getId(),autoPayUPIExistingEntity);
                log.info("autopay flag status {}",kfsStageResponseV3.getUpiAutoPayEligible());
                if (Objects.nonNull(autoPayUPIExistingEntity)) {
                    if (autoPayUPIExistingEntity.getStatus().equals(AutoPayStatusEnum.PENDING))
                    {
                        final MandateUPIStatusResponse mandateUPIStatusResponse = autoPayUPIService.checkStatus(scopeDataArgs.getMerchant(), autoPayUPIExistingEntity.getOrderId());
                        kfsStageResponseV3.setUpiAutoPayMandateStatus(mandateUPIStatusResponse.data.getStatus().name());
                    }else if(AutoPayStatusEnum.FAILED.equals(autoPayUPIExistingEntity.getStatus()) && "API_ERROR".equalsIgnoreCase(autoPayUPIExistingEntity.getErrorCode())){
                        kfsStageResponseV3.setUpiAutoPayMandateStatus(autoPayUPIExistingEntity.getStatus().name());
                        kfsStageResponseV3.setUpiAutoPayEligible(false);
                    } else {
                        kfsStageResponseV3.setUpiAutoPayMandateStatus(autoPayUPIExistingEntity.getStatus().name());
                    }
                }
                log.info("autopay flag status {}",kfsStageResponseV3.getUpiAutoPayEligible());

                if (Objects.nonNull(lendingApplication.getAgreementAt()))
                    kfsStageResponseV3.setAgreementDone(true);
                else kfsStageResponseV3.setAgreementDone(false);

            } else kfsStageResponseV3.setUpiAutoPayEligible(false);

            scopeDataArgs.setApplicationId(lendingApplication.getId());
            kfsStageResponseV3.setLender(lendingApplication.getLender());
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

    private boolean nonRegularMandateRegistrationChecks(LendingApplication lendingApplication, AutoPayUPI autoPayUPIExistingEntityMerchantId) {
        if(lendingApplication.getLender()==null) {
            log.info("lender is null in lending application for application id {}", lendingApplication.getId());
            return true;
        }

        if (autoPayUPIExistingEntityMerchantId != null && autoPayUPIExistingEntityMerchantId.getMandateEndDate() != null) {
            log.info("mandate end date is not null for application id {}", lendingApplication.getId());
            long mandateEndDateInMillis = autoPayUPIExistingEntityMerchantId.getMandateEndDate().getTime();
            long currentDateInMillis = System.currentTimeMillis();
            long differenceInMonths = (mandateEndDateInMillis - currentDateInMillis) / (1000L * 60 * 60 * 24 * 30);
            log.info("mandateEndDateInMillis {} currentDateInMillis {} differenceInMonths {}", mandateEndDateInMillis, currentDateInMillis, differenceInMonths);
            log.info("difference in months for application id {} is {}", lendingApplication.getId(), differenceInMonths);
            if ( (differenceInMonths < 36 ) || !lendingApplication.getLender().equalsIgnoreCase(autoPayUPIExistingEntityMerchantId.getLender())) {
                log.info("returning true for autopay setup");
                return true;
            }
            log.info("returning false for autopay setup");
            return false;
        }
        log.info("returning true for autopay setup");
        return true;
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
