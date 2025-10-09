package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Constants.AutoPayStatusEnum;
import com.bharatpe.lending.common.dao.AutoPayUPIDao;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.AutoPayUPI;
import com.bharatpe.lending.common.query.dao.LendingPaymentScheduleDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.PaymentConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.MandateUPIStatusResponse;
import com.bharatpe.lending.enums.ApplicationStatus;
import com.bharatpe.lending.enums.LoanStatus;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.lendingplatform.lending.service.VerifyOTPServiceV2;
import com.bharatpe.lending.lendingplatform.lms.constant.Constants;
import com.bharatpe.lending.loanV3.revamp.dto.*;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.enums.NachStatus;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.services.LendingApplicationServiceV3;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;

import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.loanV3.services.VKycService;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.bharatpe.lending.service.AutoPayUPIService;
import com.bharatpe.lending.service.helper.MandateRegistrationHelper;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;

import static com.bharatpe.lending.service.AutoPayUPIService.AUTO_PAY_UPI_APPLICABLE_LOAN_TYPES;

@Service
@Slf4j
public class UpiAutopayStageService implements IStageDataService<UpiAutopayStateDTO>{

    @Autowired
    private LendingApplicationServiceV3 lendingApplicationServiceV3;

    @Value("${upi.autopay.progress.wait.time:900}")
    Long upiAutopayProgressWaitTime;

    @Value("${upi.autopay.status.polling.interval:5}")
    Integer upiAutopayStatusPollingInterval;

    @Value("${mandate.switch.rollout.percent:10}")
    Integer mandateSwitchRolloutPercent;

    @Value("${upi.autopay.doc.upload.assc.completed.rollout.percent:0}")
    private int docUploadAsscCompletedRolloutPercent;

    @Autowired
    LoanDetailsV3Service loanDetailsV3Service;

    @Autowired
    AutoPayUPIDao autoPayUPIDao;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    AutoPayUPIService autoPayUPIService;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LoanDashboardService loanDashboardService;

    @Autowired
    VKycService vKycService;

    @Autowired
    NbfcUtils nbfcUtils;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    VerifyOTPServiceV2 verifyOTPServiceV2;

    @Autowired
    private UpiAutoPayStageHelper upiAutoPayStageHelper;

    @Autowired
    private MandateRegistrationHelper mandateRegistrationHelper;

    @Autowired
    private LendingPaymentScheduleDaoSlave lendingPaymentScheduleDaoSlave;

    @Value("${upi.autopay.force.skip.percentage:0}")
    private int upiAutoPayForceSkipPercentage;

    @Override
    public LendingStateDTO<UpiAutopayStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        log.info("Processing UPI Autopay Stage for Merchant ID: {}", scopeDataArgs.getMerchant().getId());

        // Fetching the lending state data for the current scope
        LendingStateDTO<UpiAutopayStateDTO> lendingStateDTO = fetchScopedData(scopeDataArgs);
        log.info("Lending State DTO after fetching scoped data: {}", lendingStateDTO);

        // If the mandate status is IN_PROGRESS or FAILED, set the view state to UPI_AUTOPAY_PAGE

        if(PaymentConstants.FAILED.equals(lendingStateDTO.getData().getMandateStatus())){
            if(upiAutoPayStageHelper.isEligibleForFailedForceSkip(scopeDataArgs.getApplicationId(), scopeDataArgs.getMerchant().getId() )){
                LendingViewStates nextStage = upiAutoPayStageHelper.forceSkipUpiAutopayAndGetNextPage(scopeDataArgs.getApplicationId());
                lendingStateDTO.setLendingViewStates(nextStage);
                return lendingStateDTO;
            }
        }

        if(!PaymentConstants.SUCCESS.equals(lendingStateDTO.getData().getMandateStatus())) {
            lendingStateDTO.setLendingViewStates(LendingViewStates.UPI_AUTOPAY_PAGE);
            if(easyLoanUtil.percentScaleUp(scopeDataArgs.getMerchant().getId(), upiAutoPayForceSkipPercentage)
                    && lendingStateDTO.getData().isUpiAutopayMandateEligible() && !lendingStateDTO.getData().isActiveApplicationAutoPaySetupFlow()){
                LendingApplication lendingApplication = lendingApplicationServiceV3
                        .getLendingApplication(scopeDataArgs.getApplicationId(),scopeDataArgs.getMerchant().getId());
                log.info("Setting nach status to null for applicationId: {} and loan_type: {}, as upiautopay status is not approved"
                        , lendingApplication.getId(), lendingApplication.getLoanType());
                lendingApplication.setNachStatus(null);
                lendingApplicationDao.save(lendingApplication);
            }
            return lendingStateDTO;
        }

        if(!ObjectUtils.isEmpty(lendingStateDTO) && !ObjectUtils.isEmpty(lendingStateDTO.getData()) && !ObjectUtils.isEmpty(lendingStateDTO.getData().getLoanApplication()) && easyLoanUtil.percentScaleUp(lendingStateDTO.getData().getLoanApplication().getApplicationId(), mandateSwitchRolloutPercent)) {
            log.info("Mandate switch rollout is enabled for Merchant ID: {}, proceeding with mandate switch handling", scopeDataArgs.getMerchant().getId());
            mandateSwitchNextStageHandling(scopeDataArgs, lendingStateDTO);
        }
        else{
            log.info("Mandate switch rollout is not enabled for Merchant ID: {}, proceeding with regular handling", scopeDataArgs.getMerchant().getId());
            // If the mandate status is SUCCESS and the loan application is already enach done, set the view state to APPLICATION_STATUS_PAGE
            if(!ObjectUtils.isEmpty(lendingStateDTO) && !ObjectUtils.isEmpty(lendingStateDTO.getData())
                    && !LoanType.TOPUP.name().equalsIgnoreCase(lendingStateDTO.getData().getLoanType())
                    &&  (Objects.nonNull(lendingStateDTO.getData().getLoanApplication())
                    &&  Objects.nonNull(lendingStateDTO.getData().getLoanApplication().getEnachDone())
                    &&  lendingStateDTO.getData().getLoanApplication().getEnachDone())) {
                lendingStateDTO.setLendingViewStates(LendingViewStates.APPLICATION_STATUS_PAGE);
            }
            else {
                // If the mandate status is SUCCESS, set the view state to ENACH_PAGE
                lendingStateDTO.setLendingViewStates(LendingViewStates.ENACH_PAGE);
            }
        }
        log.info("Final Lending State DTO for Merchant ID {}: {}", scopeDataArgs.getMerchant().getId(), lendingStateDTO);
        return lendingStateDTO;
    }

    private void mandateSwitchNextStageHandling(ScopeDataArgs scopeDataArgs, LendingStateDTO<UpiAutopayStateDTO> lendingStateDTO) {
        if (!ObjectUtils.isEmpty(lendingStateDTO) && !ObjectUtils.isEmpty(lendingStateDTO.getData())) {
            updateNextLendingStateAfterUpiAutopayDoneForApplication(lendingStateDTO);
        }

        // If the mandate status is SUCCESS and the loan application is already enach done, set the view state to APPLICATION_STATUS_PAGE
        if (!ObjectUtils.isEmpty(lendingStateDTO) && !ObjectUtils.isEmpty(lendingStateDTO.getData())
                && !LoanType.TOPUP.name().equalsIgnoreCase(lendingStateDTO.getData().getLoanType())
                && (Objects.nonNull(lendingStateDTO.getData().getLoanApplication())
                && Objects.nonNull(lendingStateDTO.getData().getLoanApplication().getEnachDone())
                && lendingStateDTO.getData().getLoanApplication().getEnachDone())) {

            log.info("UPI Autopay Mandate is SUCCESS and Enach is already done for Merchant ID: {}", scopeDataArgs.getMerchant().getId());

            if (LoanType.TOPUP.name().equalsIgnoreCase(lendingStateDTO.getData().getLoanType())) {
                lendingStateDTO.setLendingViewStates(LendingViewStates.AGREEMENT_PAGE);
            } else {
                lendingStateDTO.setLendingViewStates(vKycService.getLenderVkycPageOrDefault(LendingViewStates.APPLICATION_STATUS_PAGE, lendingStateDTO.getData().getMerchantId(), lendingStateDTO.getData().getLender(), false));
            }
        }
    }

    private void updateNextLendingStateAfterUpiAutopayDoneForApplication(LendingStateDTO<UpiAutopayStateDTO> lendingStateDTO) {
        if (lendingStateDTO.getData().isEnachEligible()) {
            lendingStateDTO.setLendingViewStates(LendingViewStates.ENACH_PAGE);
        } else {
            lendingStateDTO.setLendingViewStates(
                    LoanType.TOPUP.name().equalsIgnoreCase(lendingStateDTO.getData().getLoanType()) ?
                            LendingViewStates.AGREEMENT_PAGE :
                            vKycService.getLenderVkycPageOrDefault(LendingViewStates.APPLICATION_STATUS_PAGE, lendingStateDTO.getData().getMerchantId(), lendingStateDTO.getData().getLender(), false)
            );
        }
    }

    @Override
    public LendingStateDTO<UpiAutopayStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        log.info("Fetching UPI Autopay Stage Data for Merchant ID: {}", scopeDataArgs.getMerchant().getId());
        UpiAutopayStateDTO upiAutopayStateDTO = new UpiAutopayStateDTO();
        upiAutopayStateDTO.setMerchantId(scopeDataArgs.getMerchant().getId());
        boolean isActiveApplicationAutoPaySetupFlow=false;
        LendingPaymentScheduleSlave lps = lendingPaymentScheduleDaoSlave.findByMerchantIdAndStatus(scopeDataArgs.getMerchant().getId(), Collections.singletonList(LoanStatus.ACTIVE.name()));
        if(Objects.isNull(scopeDataArgs.getApplicationId()) && Objects.nonNull(scopeDataArgs.getLoanDetailsV3Request()) && scopeDataArgs.getLoanDetailsV3Request().isAutoPayPending()){
            if(!mandateRegistrationHelper.isAutopayRequiredForActiveApplication(lps)){
                throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_ELIGIBLE_FOR_UPI_AUTO_PAY.getErrorCode(), LoanDetailExceptionEnum.APPLICATION_NOT_ELIGIBLE_FOR_UPI_AUTO_PAY.getErrorMessage());
            }
            scopeDataArgs.setApplicationId(lps.getApplicationId());
        }
        if(Objects.nonNull(lps) && "ACTIVE".equalsIgnoreCase(lps.getStatus())){
            isActiveApplicationAutoPaySetupFlow=true;
        }
        upiAutopayStateDTO.setActiveApplicationAutoPaySetupFlow(isActiveApplicationAutoPaySetupFlow);
        LendingApplication lendingApplication = lendingApplicationServiceV3.getLendingApplication(scopeDataArgs.getApplicationId(),scopeDataArgs.getMerchant().getId());
        if (ObjectUtils.isEmpty(lendingApplication)) {
            log.info("Application not found for {}", scopeDataArgs.getMerchant().getId());
            throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorMessage());
        }
        log.info("Lending Application for Merchant ID {}: {}", scopeDataArgs.getMerchant().getId(), lendingApplication);

        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
        if(ObjectUtils.isEmpty(lendingApplicationDetails)) {
            log.info("Lending Application Details not found for {}", lendingApplication.getId());
            throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_DETAILS_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_DETAILS_NOT_FOUND.getErrorMessage());
        }

        upiAutopayStateDTO.setApplicationId(lendingApplication.getId());
        upiAutopayStateDTO.setLender(lendingApplication.getLender());
        upiAutopayStateDTO.setLoanAmount(lendingApplication.getLoanAmount());
        upiAutopayStateDTO.setTenure(lendingApplication.getTenureInMonths());
        upiAutopayStateDTO.setLoanType(lendingApplication.getLoanType());
        upiAutopayStateDTO.setWaitTime(upiAutopayProgressWaitTime);
        upiAutopayStateDTO.setPollingTime(upiAutopayStatusPollingInterval);
        LoanApplicationDetailsV3 loanApplicationDetails = setApplicationDetails(lendingApplication, isActiveApplicationAutoPaySetupFlow);
        upiAutopayStateDTO.setLoanApplication(loanApplicationDetails);

        upiAutopayStateDTO.setBankDetails(loanUtil.getAccountDetails(scopeDataArgs.getMerchant().getId()));

        AutoPayUPI existingAutoPayUpiWithMerchantId = autoPayUPIDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(lendingApplication.getMerchantId(),AutoPayStatusEnum.ACTIVE.name());
        log.info("Existing AutoPay UPI for Merchant ID {}: {}", lendingApplication.getMerchantId(), existingAutoPayUpiWithMerchantId);

        boolean autoPayUpiSkipped= autoPayUPIService.isEligibleForUpiAutoPaySkip(lendingApplication, existingAutoPayUpiWithMerchantId);
        AutoPayUPI existingAutoPayUPI = null;
        if(AUTO_PAY_UPI_APPLICABLE_LOAN_TYPES.contains(lendingApplication.getLoanType()) && autoPayUpiSkipped){
            existingAutoPayUPI = autoPayUPIService.cloneAutoPayUpiEntityForNewApplication(existingAutoPayUpiWithMerchantId, lendingApplication.getId());
            log.info("AutoPay UPI skipped for merchantId : {}", lendingApplication.getMerchantId());
            lendingApplication.setUpiAutopayStatus("APPROVED");
            lendingApplicationDao.save(lendingApplication);
            log.info("Lending application updated with UPI Autopay status: APPROVED for topup application ID: {}", lendingApplication.getId());
        }
        if(LoanType.REGULAR.name().equalsIgnoreCase(lendingApplication.getLoanType()) && !ObjectUtils.isEmpty(existingAutoPayUpiWithMerchantId) && Boolean.FALSE.equals(autoPayUpiSkipped)){
            // revoke previous mandate
            autoPayUPIService.revokeMandate(lendingApplication, existingAutoPayUpiWithMerchantId);
        }

        existingAutoPayUPI = Optional.ofNullable(existingAutoPayUPI).orElse(autoPayUPIDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId()));
        log.info("Autopay Upi Mandate found for Application Id: {} : {}", lendingApplication.getId(), existingAutoPayUPI);

        // If the existing AutoPay UPI is not null, check its status and set the mandate status accordingly
        if (Objects.nonNull(existingAutoPayUPI)) {
            if (AutoPayStatusEnum.PENDING.equals(existingAutoPayUPI.getStatus()))
            {
                final MandateUPIStatusResponse mandateUPIStatusResponse = autoPayUPIService.checkStatus(scopeDataArgs.getMerchant(), existingAutoPayUPI.getOrderId());
                upiAutopayStateDTO.setMandateStatus(PaymentConstants.UPI_AUTOPAY_MANDATE_STATUS_MAP.getOrDefault(mandateUPIStatusResponse.data.getStatus(), mandateUPIStatusResponse.data.getStatus().name()));
            } else if(AutoPayStatusEnum.FAILED.equals(existingAutoPayUPI.getStatus())){
                upiAutopayStateDTO.setMandateStatus(PaymentConstants.UPI_AUTOPAY_MANDATE_STATUS_MAP.getOrDefault(existingAutoPayUPI.getStatus(), existingAutoPayUPI.getStatus().name()));
            } else {
                upiAutopayStateDTO.setMandateStatus(PaymentConstants.UPI_AUTOPAY_MANDATE_STATUS_MAP.getOrDefault(existingAutoPayUPI.getStatus(), existingAutoPayUPI.getStatus().name()));
            }
            upiAutopayStateDTO.setCreatedAt(existingAutoPayUPI.getCreatedAt().getTime());
            upiAutopayStateDTO.setRetryCount(0);
        }
        else{
            upiAutopayStateDTO.setMandateStatus("INIT");
            upiAutopayStateDTO.setErrorCode(null);
            upiAutopayStateDTO.setErrorReason(null);
            upiAutopayStateDTO.setCreatedAt(null);
            upiAutopayStateDTO.setRetryCount(0);
        }

        if(PaymentConstants.FAILED.equals(upiAutopayStateDTO.getMandateStatus())){
            log.info("UPI Autopay mandate status is FAILED for application: {}, setting Error details for application", lendingApplication.getId());

            updateErrorDetails(upiAutopayStateDTO, existingAutoPayUPI);
            log.info("Error details updated for UPI Autopay State DTO: {}", upiAutopayStateDTO);
        }

        if (PaymentConstants.SUCCESS.equalsIgnoreCase(upiAutopayStateDTO.getMandateStatus())) {
            if (existingAutoPayUPI.isStandaloneAutopaySetup() && Constants.DISBURSED_LOAN.equalsIgnoreCase(lendingApplication.getLoanDisbursalStatus())) {
                log.info("Setting current loan active at UPI Autopay stage for application: {}", lendingApplication.getId());
                upiAutopayStateDTO.setCurrentLoanActive(true);
                return new LendingStateDTO<>(upiAutopayStateDTO , LendingViewStates.UPI_AUTOPAY_PAGE, LendingViewStates.UPI_AUTOPAY_PAGE);
            }
            if (loanUtil.isMandateSwitchEnabled(lendingApplication) && !lendingApplicationDetails.isNachEligible() && lendingApplicationDetails.isAutoPayUpiEligible()) {
                log.info("UPI Autopay mandate is SUCCESS for application: {}, and Nach is ineligible, invoking doc upload workflow", lendingApplication.getId());
                upiAutoPayStageHelper.invokeDocUploadFlow(lendingApplication);
                lendingApplication.setNachStatus(NachStatus.APPROVED.name());
                lendingApplication.setNachType("ENACH");
                lendingApplicationDao.save(lendingApplication);
            }
        }

        log.info("Autopay UPI Mandate status for application: {}: {}", lendingApplication.getId(), upiAutopayStateDTO.getMandateStatus());

        if(easyLoanUtil.percentScaleUp(lendingApplication.getId(), mandateSwitchRolloutPercent)) {
            log.info("Setting if Nach is eligible for application: {}", lendingApplication.getId());
            if(isActiveApplicationAutoPaySetupFlow){
                upiAutopayStateDTO.setUpiAutopayMandateEligible(true);
                upiAutopayStateDTO.setEnachEligible(false);
            }else {
                loanUtil.updateMandateColumnsInLAD(lendingApplicationDetails, lendingApplication.getLender(), lendingApplication.getLoanAmount(), scopeDataArgs.getLoanDetailsV3Request().isIOS());
                upiAutopayStateDTO.setUpiAutopayMandateEligible(lendingApplicationDetails.isAutoPayUpiEligible());
                upiAutopayStateDTO.setEnachEligible(lendingApplicationDetails.isNachEligible());
            }
        }

        if(Objects.nonNull(upiAutopayStateDTO.getMandateStatus()) && PaymentConstants.SUCCESS.equals(upiAutopayStateDTO.getMandateStatus())){
            // TODO: Next stage will change based on config, so we need to update this logic.
            loanDetailsV3Service.saveApplicationViewState(null, lendingApplication.getId(), LendingViewStates.ENACH_PAGE);
        }
        else if(!isActiveApplicationAutoPaySetupFlow){
            loanDetailsV3Service.saveApplicationViewState(null, lendingApplication.getId(), LendingViewStates.UPI_AUTOPAY_PAGE);
        }

        log.info("Upi Autopay Stage Response for {} : {}", scopeDataArgs.getMerchant().getId(), upiAutopayStateDTO);
        return new LendingStateDTO<>(upiAutopayStateDTO , LendingViewStates.UPI_AUTOPAY_PAGE, LendingViewStates.UPI_AUTOPAY_PAGE);
    }

    private void updateErrorDetails(UpiAutopayStateDTO upiAutopayStateDTO, AutoPayUPI existingAutoPayUPI) {
        log.info("Updating error details for UPI Autopay State DTO: {}", upiAutopayStateDTO);
        upiAutopayStateDTO.setErrorCode(existingAutoPayUPI.getErrorCode());
        upiAutopayStateDTO.setErrorReason(existingAutoPayUPI.getErrorMessage());
        String displayMessage = PaymentConstants.UPI_AUTOPAY_ERROR_CODE_TO_DISPLAY_MESSAGE_MAP.getOrDefault(existingAutoPayUPI.getErrorCode(), "AutoPay failed");
        upiAutopayStateDTO.setDisplayMessage(displayMessage);
        upiAutopayStateDTO.setRetrySuggested(PaymentConstants.UPI_AUTOPAY_ERROR_CODE_TO_RETRY_ELIGIBLE_MAP.getOrDefault(existingAutoPayUPI.getErrorCode(), true));
    }


    public LoanApplicationDetailsV3 setApplicationDetails(LendingApplication openApplication, boolean isActiveApplicationFlow) {
        log.info("Setting application details for open application: {}", openApplication.getId());
        LoanApplicationDetailsV3 applicationDetails = new LoanApplicationDetailsV3();
        applicationDetails.setApplicationId(openApplication.getId());
        applicationDetails.setLoanAmount(openApplication.getLoanAmount());

        if (!loanUtil.isEnachBank(openApplication.getMerchantId())) {
            log.info("bank not nachable for {}", openApplication.getId());
            throw new LoanDetailsException(LoanDetailExceptionEnum.NON_NACHABLE_BANK.getErrorCode(),LoanDetailExceptionEnum.NON_NACHABLE_BANK.getErrorMessage());
        }
        boolean isEligibleForNachSkip = loanUtil.isEligibleForNachSkip(openApplication, openApplication.getLender(), true);
        if (easyLoanUtil.isDummyMerchant(openApplication.getMerchantId()) || isEligibleForNachSkip
                ||loanUtil.isEnachDone(openApplication.getMerchantId(), openApplication.getId())) {
            if(ObjectUtils.isEmpty(openApplication.getNachStatus())){
                loanDashboardService.deleteLoanDashboardCache(openApplication.getMerchantId());
            }
            openApplication.setNachStatus("APPROVED");
            openApplication.setNachType("ENACH");
            openApplication.setNachLender(loanUtil.enachServiceLenderMapper(openApplication.getLender()));
            if(!isActiveApplicationFlow){
                lendingApplicationDao.save(openApplication);
                loanDetailsV3Service.saveApplicationViewState(null, openApplication.getId(), LendingViewStates.APPLICATION_STATUS_PAGE);
            }
        }
        applicationDetails.setSkipEnach(isEligibleForNachSkip);
        if (ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(openApplication.getStatus())) {
            applicationDetails.setEnachDone("APPROVED".equalsIgnoreCase(openApplication.getNachStatus()));
        }
        if("TOPUP".equalsIgnoreCase(openApplication.getLoanType()) && ApplicationStatus.DRAFT.name().equalsIgnoreCase(openApplication.getStatus())){
            applicationDetails.setEnachDone("APPROVED".equalsIgnoreCase(openApplication.getNachStatus()));
        }
        return applicationDetails;
    }
}
