package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.lending.common.Constants.AutoPayStatusEnum;
import com.bharatpe.lending.common.dao.AutoPayUPIDao;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.entity.AutoPayUPI;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.enums.MandateType;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dto.MandateUPIStatusResponse;
import com.bharatpe.lending.enums.LoanType;
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
import com.bharatpe.lending.loanV3.services.VKycService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import static com.bharatpe.lending.service.AutoPayUPIService.AUTO_PAY_UPI_APPLICABLE_LOAN_TYPES;

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
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    AutoPayUPIDao autoPayUPIDao;

    @Autowired
    AutoPayUPIService autoPayUPIService;

    @Value("${enable.autopayupi.registration:false}")
    private Boolean enableAutopayUPIRegistration;

    @Value("${merchant.plugin.rollout.percent:0}")
    Integer merchantPluginRolloutPercent;

    @Autowired
    VKycService vkycService;

    @Value("${upi.autopay.force.skip.percentage:0}")
    private int upiAutoPayForceSkipPercentage;

    @Override
    public LendingStateDTO<KFSStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<KFSStateDTO> lendingStateDTO = fetchScopedData(scopeDataArgs);

        if (Objects.nonNull(lendingStateDTO.getData().getUpiAutoPayEligible()) && lendingStateDTO.getData().getUpiAutoPayEligible() && !lendingStateDTO.getData().isDedicatedUpiAutoPayScreenEligible()) {
            // we want the user to be on the kfs page until the autopayupi is successfully done
            if (!AutoPayStatusEnum.ACTIVE.name().equals(lendingStateDTO.getData().getUpiAutoPayMandateStatus())) {
                lendingStateDTO.setLendingViewStates(LendingViewStates.KEY_FACTOR_STATEMENT_PAGE);
                loanDetailsV3Service.saveApplicationViewState(null, scopeDataArgs.getApplicationId(), LendingViewStates.KEY_FACTOR_STATEMENT_PAGE);
                return lendingStateDTO;
            } else {
                lendingStateDTO.setLendingViewStates(LendingViewStates.ENACH_PAGE);
            }

        } else lendingStateDTO.setLendingViewStates(LendingViewStates.ENACH_PAGE);

        Optional<LendingApplication> optionalLendingApplication = lendingApplicationDao.findById(scopeDataArgs.getApplicationId());
        if(!optionalLendingApplication.isPresent()){
            log.error("Lending application not found for applicationId: {}", scopeDataArgs.getApplicationId());
            return null;
        }
        LendingApplication lendingApplication = optionalLendingApplication.get();
        log.info("Fetched lending application for applicationId: {}: {}", scopeDataArgs.getApplicationId(), lendingApplication);

        if(!ObjectUtils.isEmpty(lendingStateDTO) && !ObjectUtils.isEmpty(lendingStateDTO.getData()) && loanUtil.isMandateSwitchEnabled(lendingApplication)) {
            log.info("Mandate switch rollout is enabled for applicationId: {}", scopeDataArgs.getApplicationId());
            mandateSwitchNextStageHandling(scopeDataArgs, lendingStateDTO);
        }
        else if(!ObjectUtils.isEmpty(lendingStateDTO) && !ObjectUtils.isEmpty(lendingStateDTO.getData()) && !loanUtil.isMandateSwitchEnabled(lendingApplication)){
            log.info("Mandate switch rollout is not enabled for applicationId: {}", scopeDataArgs.getApplicationId());
            nextStageHandling(scopeDataArgs, lendingStateDTO);
        }
        if("APPROVED".equals(lendingApplication.getNachStatus())
                && Objects.nonNull(lendingStateDTO.getData()) && lendingStateDTO.getData().isUpiAutopayMandateEligible()
                     && !"APPROVED".equals(lendingStateDTO.getData().getUpiAutoPayMandateStatus())
                        && easyLoanUtil.percentScaleUp(lendingApplication.getMerchantId(), upiAutoPayForceSkipPercentage)){
            log.info("Setting nach status to null for applicationId: {} and loan_type: {}, as upiautopay status is not approved"
                    , lendingApplication.getId(), lendingApplication.getLoanType());
            lendingApplication.setNachStatus(null);
            lendingApplicationDao.save(lendingApplication);
        }

        loanDetailsV3Service.saveApplicationViewState(null, scopeDataArgs.getApplicationId(), LendingViewStates.KEY_FACTOR_STATEMENT_PAGE);
        return lendingStateDTO;
    }

    private void nextStageHandling(ScopeDataArgs scopeDataArgs, LendingStateDTO<KFSStateDTO> lendingStateDTO) {
        log.info("Handling next stage for application id {}", scopeDataArgs.getApplicationId());
        if(!ObjectUtils.isEmpty(lendingStateDTO) && !ObjectUtils.isEmpty(lendingStateDTO.getData())){
            if((Objects.nonNull(lendingStateDTO.getData().getLoanApplication()) &&
                    Objects.nonNull(lendingStateDTO.getData().getLoanApplication().getEnachDone()) &&
                    lendingStateDTO.getData().getLoanApplication().getEnachDone())
                    ||

                    (Objects.nonNull(lendingStateDTO.getData().getTopupLoanApplication()) &&
                            Objects.nonNull(lendingStateDTO.getData().getTopupLoanApplication().getEnachDone()) &&
                            lendingStateDTO.getData().getTopupLoanApplication().getEnachDone())) {
                lendingStateDTO.setLendingViewStates(vkycService.getLenderVkycPageOrDefault(LendingViewStates.APPLICATION_STATUS_PAGE, lendingStateDTO.getData().getMerchantId(), lendingStateDTO.getData().getLender(), true));
            }
        }

        if(!ObjectUtils.isEmpty(lendingStateDTO) && !ObjectUtils.isEmpty(lendingStateDTO.getData()) &&
                lendingStateDTO.getData().isDedicatedUpiAutoPayScreenEligible() && ObjectUtils.isEmpty(lendingStateDTO.getData().getTopupLoanApplication())) {
            lendingStateDTO.setLendingViewStates(LendingViewStates.UPI_AUTOPAY_PAGE);
        }
    }

    private void mandateSwitchNextStageHandling(ScopeDataArgs scopeDataArgs, LendingStateDTO<KFSStateDTO> lendingStateDTO) {
        log.info("Mandate switch rollout is enabled, handling next stage for application id {}", scopeDataArgs.getApplicationId());
        if(Objects.nonNull(lendingStateDTO.getData().getTopupLoanApplication())) {
            log.info("Topup loan application exists for application id {}, setting next Lending State", scopeDataArgs.getApplicationId());
            lendingStateDTO.setLendingViewStates(vkycService.getLenderVkycPageOrDefault(LendingViewStates.APPLICATION_STATUS_PAGE, lendingStateDTO.getData().getMerchantId(), lendingStateDTO.getData().getLender(), true));
            log.info("Next Lending State for Topup Loan Application: {}", lendingStateDTO.getLendingViewStates());
            return;
        }
        if (!ObjectUtils.isEmpty(lendingStateDTO) && !ObjectUtils.isEmpty(lendingStateDTO.getData()) &&
                lendingStateDTO.getData().isDedicatedUpiAutoPayScreenEligible() && ObjectUtils.isEmpty(lendingStateDTO.getData().getTopupLoanApplication())) {
            updateNextLendingStateForNonTopupDedicatedScreenEligibleApplication(lendingStateDTO);
        }

        log.info("Current Lending State: {} and checking if UPI Autopay is already done for application id {}", lendingStateDTO.getLendingViewStates(), scopeDataArgs.getApplicationId());
        if (!ObjectUtils.isEmpty(lendingStateDTO) && !ObjectUtils.isEmpty(lendingStateDTO.getData())
                && LendingViewStates.UPI_AUTOPAY_PAGE.equals(lendingStateDTO.getLendingViewStates())
                && isUpiAutopayAlreadyDone(scopeDataArgs)) {
            log.info("UPI Autopay already done for application id {}, setting next Lending State", scopeDataArgs.getApplicationId());
            updateNextLendingStateAfterUpiAutopayDoneForNonTopupApplication(lendingStateDTO);
            log.info("Next Lending State after UPI Autopay done: {}", lendingStateDTO.getLendingViewStates());
        }

        log.info("Current Lending State: {} and checking if Enach is already done for application id {}", lendingStateDTO.getLendingViewStates(), scopeDataArgs.getApplicationId());
        if (!ObjectUtils.isEmpty(lendingStateDTO) && !ObjectUtils.isEmpty(lendingStateDTO.getData()) && LendingViewStates.ENACH_PAGE.equals(lendingStateDTO.getLendingViewStates())) {
            if ((Objects.nonNull(lendingStateDTO.getData().getLoanApplication()) &&
                    Objects.nonNull(lendingStateDTO.getData().getLoanApplication().getEnachDone()) &&
                    lendingStateDTO.getData().getLoanApplication().getEnachDone())
                    ||

                    (Objects.nonNull(lendingStateDTO.getData().getTopupLoanApplication()) &&
                            Objects.nonNull(lendingStateDTO.getData().getTopupLoanApplication().getEnachDone()) &&
                            lendingStateDTO.getData().getTopupLoanApplication().getEnachDone())) {
                lendingStateDTO.setLendingViewStates(vkycService.getLenderVkycPageOrDefault(LendingViewStates.APPLICATION_STATUS_PAGE, lendingStateDTO.getData().getMerchantId(), lendingStateDTO.getData().getLender(), Objects.nonNull(lendingStateDTO.getData().getTopupLoanApplication())));
            }
        }
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

            if(lendingApplicationDetails.getMandateFlagsToggledOn() != null || loanUtil.isMandateSwitchEnabled(lendingApplication)) {
                loanUtil.updateMandateColumnsInLAD(lendingApplicationDetails, lendingApplication.getLender(), lendingApplication.getLoanAmount(), scopeDataArgs.getLoanDetailsV3Request().isIOS());
                kfsStageResponseV3.setUpiAutopayMandateEligible(lendingApplicationDetails.isAutoPayUpiEligible());
                kfsStageResponseV3.setEnachEligible(lendingApplicationDetails.isNachEligible());
            }
            log.info("Kfs stage response for application id {} is {}", lendingApplication.getId(), kfsStageResponseV3);
            boolean isEligibleForAutoPayUPI = enableAutopayUPIRegistration
                    && loanUtil.isApplicationEligibleForAutoPayUpi(lendingApplication.getLender(), lendingApplication.getMerchantId(), lendingApplication.getLoanAmount())
                    && !loanUtil.checkIfUpiAutoPayNotRequired(lendingApplication)
                    && easyLoanUtil.percentScaleUp(lendingApplication.getMerchantId(), merchantPluginRolloutPercent);
            boolean isEligibleForUpiNach = MandateType.DIGIO_UPI.equals(lendingApplicationDetails.getMandateType());

            if (isEligibleForAutoPayUPI && isEligibleForUpiNach && !scopeDataArgs.getLoanDetailsV3Request().isIOS()) {
                kfsStageResponseV3.setUpiAutoPayEligible(false);
            } else if (isEligibleForAutoPayUPI) {
                boolean isDedicatedScreenEligible = loanUtil.isUpiDedicatedScreenEligible(lendingApplication);
                if (!isDedicatedScreenEligible) {
                    log.info("setting autopay for application id {}", lendingApplication.getId());
                    kfsStageResponseV3.setDedicatedUpiAutoPayScreenEligible(false);

                    AutoPayUPI autoPayUPIExistingEntityMerchantId = autoPayUPIDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(
                            lendingApplication.getMerchantId(), AutoPayStatusEnum.ACTIVE.name());
                    boolean autoPayUpiSkipped= autoPayUPIService.isEligibleForUpiAutoPaySkip(lendingApplication,autoPayUPIExistingEntityMerchantId);
                    kfsStageResponseV3.setUpiAutoPayEligible(Boolean.FALSE.equals(autoPayUpiSkipped));
                    AutoPayUPI autoPayUPIExistingEntity = null;
                    if (AUTO_PAY_UPI_APPLICABLE_LOAN_TYPES.contains(lendingApplication.getLoanType()) && autoPayUpiSkipped) {
                        autoPayUPIExistingEntity = autoPayUPIService.cloneAutoPayUpiEntityForNewApplication(autoPayUPIExistingEntityMerchantId, lendingApplication.getId());
                    }
                    if (LoanType.REGULAR.name().equalsIgnoreCase(lendingApplication.getLoanType()) && autoPayUPIExistingEntityMerchantId != null && Boolean.FALSE.equals(autoPayUpiSkipped)) {
                        autoPayUPIService.revokeMandate(lendingApplication, autoPayUPIExistingEntityMerchantId);
                    }

                    autoPayUPIExistingEntity = Optional.ofNullable(autoPayUPIExistingEntity).orElse(autoPayUPIDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId()));
                    log.info("autopay upi found for application id {} is {}", lendingApplication.getId(), autoPayUPIExistingEntity);

                    if (autoPayUPIExistingEntity != null) {
                        AutoPayStatusEnum status = autoPayUPIExistingEntity.getStatus();
                        if (AutoPayStatusEnum.PENDING.equals(status)) {
                            MandateUPIStatusResponse mandateUPIStatusResponse = autoPayUPIService.checkStatus(
                                    scopeDataArgs.getMerchant(), autoPayUPIExistingEntity.getOrderId());
                            kfsStageResponseV3.setUpiAutoPayMandateStatus(mandateUPIStatusResponse.data.getStatus().name());
                        } else {
                            kfsStageResponseV3.setUpiAutoPayMandateStatus(status.name());
                            if (AutoPayStatusEnum.FAILED.equals(status)
                                    && "API_ERROR".equalsIgnoreCase(autoPayUPIExistingEntity.getErrorCode())) {
                                kfsStageResponseV3.setUpiAutoPayEligible(false);
                            }
                        }
                    }

                    kfsStageResponseV3.setAgreementDone(Objects.nonNull(lendingApplication.getAgreementAt()));

                } else {
                    log.info("setting dedicated upi autopay for application id {} true", lendingApplication.getId());
                    if (LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())) {
                        AutoPayUPI autoPayUPIExistingEntity = autoPayUPIDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId());
                        log.info("autopay upi found for application id {} is {}", lendingApplication.getId(), autoPayUPIExistingEntity);
                        if (autoPayUPIExistingEntity != null) {
                            kfsStageResponseV3.setUpiAutoPayMandateStatus(autoPayUPIExistingEntity.getStatus().name());
                        }
                    }
                    kfsStageResponseV3.setDedicatedUpiAutoPayScreenEligible(true);
                    kfsStageResponseV3.setUpiAutoPayEligible(false);
                }

            } else {
                kfsStageResponseV3.setUpiAutoPayEligible(false);
            }

            scopeDataArgs.setApplicationId(lendingApplication.getId());
            kfsStageResponseV3.setLender(lendingApplication.getLender());
            if(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())){
                LendingApplication topupApplication = lendingApplicationDao.findOpenTopUpApplication(scopeDataArgs.getMerchant().getId(), LoanType.TOPUP.name());
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
        Boolean isEligibleForSkipNach = loanUtil.isEligibleForNachSkip(openApplication, openApplication.getLender(), true);
        if (easyLoanUtil.isDummyMerchant(openApplication.getMerchantId()) || isEligibleForSkipNach ||
                loanUtil.isEnachDone(openApplication.getMerchantId(), openApplication.getId())) {
            if(ObjectUtils.isEmpty(openApplication.getNachStatus())){
                loanDashboardService.deleteLoanDashboardCache(openApplication.getMerchantId());
            }
            openApplication.setNachStatus("APPROVED");
            openApplication.setNachType("ENACH");
            openApplication.setNachLender(loanUtil.enachServiceLenderMapper(openApplication.getLender()));
            lendingApplicationDao.save(openApplication);
            loanDetailsV3Service.saveApplicationViewState(null, openApplication.getId(), vkycService.getLenderVkycPageOrDefault(LendingViewStates.APPLICATION_STATUS_PAGE, openApplication.getMerchantId(), openApplication.getLender(), LoanType.TOPUP.name().equalsIgnoreCase(openApplication.getLoanType())));
        }
        applicationDetails.setSkipEnach(isEligibleForSkipNach);
        if (ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(openApplication.getStatus())) {
            applicationDetails.setEnachDone("APPROVED".equalsIgnoreCase(openApplication.getNachStatus()));
        }
        if(LoanType.TOPUP.name().equalsIgnoreCase(openApplication.getLoanType()) && ApplicationStatus.DRAFT.name().equalsIgnoreCase(openApplication.getStatus())){
            applicationDetails.setEnachDone("APPROVED".equalsIgnoreCase(openApplication.getNachStatus()));
        }
        return applicationDetails;
    }


    private boolean isUpiAutopayAlreadyDone(ScopeDataArgs scopeDataArgs) {
        LendingApplication lendingApplication = lendingApplicationServiceV3.getLendingApplication(scopeDataArgs.getApplicationId(), scopeDataArgs.getMerchant().getId());

        AutoPayUPI autoPayUPIExistingEntityMerchantId = autoPayUPIDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(lendingApplication.getMerchantId(),"ACTIVE");
        boolean autoPayUpiSkipped= autoPayUPIService.isEligibleForUpiAutoPaySkip(lendingApplication,autoPayUPIExistingEntityMerchantId);
        AutoPayUPI autoPayUPIExistingEntity = null;
        if(AUTO_PAY_UPI_APPLICABLE_LOAN_TYPES.contains( lendingApplication.getLoanType()) && autoPayUpiSkipped){
            autoPayUPIExistingEntity = autoPayUPIService.cloneAutoPayUpiEntityForNewApplication(autoPayUPIExistingEntityMerchantId, lendingApplication.getId());
            log.info("AutoPay UPI skipped for merchantId : {}", lendingApplication.getMerchantId());
            lendingApplication.setUpiAutopayStatus("APPROVED");
            lendingApplicationDao.save(lendingApplication);
            log.info("Lending application updated with UPI Autopay status: APPROVED for topup application ID: {}", lendingApplication.getId());
        }

        if(LoanType.REGULAR.name().equalsIgnoreCase( lendingApplication.getLoanType()) && autoPayUPIExistingEntityMerchantId != null && Boolean.FALSE.equals(autoPayUpiSkipped)){
            // revoke previous mandate
            autoPayUPIService.revokeMandate(lendingApplication,autoPayUPIExistingEntityMerchantId);
        }

        autoPayUPIExistingEntity =  Optional.ofNullable(autoPayUPIExistingEntity).orElse(autoPayUPIDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId()));

        return (!ObjectUtils.isEmpty(autoPayUPIExistingEntity) && AutoPayStatusEnum.ACTIVE.equals(autoPayUPIExistingEntity.getStatus()));
    }

    private void updateNextLendingStateAfterUpiAutopayDoneForNonTopupApplication(LendingStateDTO<KFSStateDTO> lendingStateDTO) {
        if (lendingStateDTO.getData().isEnachEligible()) {
            lendingStateDTO.setLendingViewStates(LendingViewStates.ENACH_PAGE);
        } else {
            lendingStateDTO.setLendingViewStates(vkycService.getLenderVkycPageOrDefault(LendingViewStates.APPLICATION_STATUS_PAGE, lendingStateDTO.getData().getMerchantId(), lendingStateDTO.getData().getLender(), false));
        }
    }

    private void updateNextLendingStateForNonTopupDedicatedScreenEligibleApplication(LendingStateDTO<KFSStateDTO> lendingStateDTO) {
        if(lendingStateDTO.getData().isUpiAutopayMandateEligible()) {
            lendingStateDTO.setLendingViewStates(LendingViewStates.UPI_AUTOPAY_PAGE);
        } else if (lendingStateDTO.getData().isEnachEligible()) {
            lendingStateDTO.setLendingViewStates(LendingViewStates.ENACH_PAGE);
        } else {
            lendingStateDTO.setLendingViewStates(vkycService.getLenderVkycPageOrDefault(LendingViewStates.APPLICATION_STATUS_PAGE, lendingStateDTO.getData().getMerchantId(), lendingStateDTO.getData().getLender(), false));
        }
    }
}
