package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Constants.AutoPayStatusEnum;
import com.bharatpe.lending.common.Kafka.Producer.ConfluentKafkaProducer;
import com.bharatpe.lending.common.dao.AutoPayUPIDao;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.entity.AutoPayUPI;
import com.bharatpe.lending.common.enums.MandateType;
import com.bharatpe.lending.common.query.dao.LendingPaymentScheduleDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.constant.PaymentConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.BottomSheetEvent;
import com.bharatpe.lending.dto.MandateUPIStatusResponse;
import com.bharatpe.lending.dto.PushDataToHomepageCarouselDto;
import com.bharatpe.lending.enums.LoanStatus;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.lendingplatform.lms.constant.Constants;
import com.bharatpe.lending.loanV3.revamp.dto.*;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.enums.NachStatus;
import com.bharatpe.lending.loanV3.revamp.enums.UpiAutoPayStatus;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.services.LendingApplicationServiceV3;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;

import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.loanV3.services.VKycService;
import com.bharatpe.lending.service.AutoPayUPIService;
import com.bharatpe.lending.service.helper.MandateRegistrationHelper;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.validation.constraints.NotNull;
import java.math.BigInteger;
import java.util.*;

import static com.bharatpe.lending.constant.LendingConstants.NACH_TYPE;
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
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LoanDashboardService loanDashboardService;

    @Autowired
    VKycService vKycService;

    @Autowired
    private UpiAutoPayStageHelper upiAutoPayStageHelper;

    @Autowired
    private MandateRegistrationHelper mandateRegistrationHelper;

    @Autowired
    private LendingPaymentScheduleDaoSlave lendingPaymentScheduleDaoSlave;
    @Autowired
    ConfluentKafkaProducer confluentKafkaProducer;

    @Value("${upi.autopay.force.skip.percentage:0}")
    private int upiAutoPayForceSkipPercentage;

    @Value("${bottom.sheet.topic:max_home_page_upi_mandate}")
    private String bottomSheetTopic;

    @Value("${push_data.homepage.carousel.topic:max_home_page_merchant_carousel}")
    private String pushDataToHomePageCarouselTopic;

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
        if(!PaymentConstants.SUCCESS.equals(lendingStateDTO.getData().getMandateStatus())
            || lendingStateDTO.getData().isActiveApplicationAutoPaySetupFlow()) {
            lendingStateDTO.setLendingViewStates(LendingViewStates.UPI_AUTOPAY_PAGE);
            return lendingStateDTO;
        }
        // handle autopay success next page
        LendingViewStates nextState=lendingStateDTO.getData().getAutoPaySuccessNextState();
        lendingStateDTO.setLendingViewStates(nextState);
        log.info("Final Lending State DTO for Merchant ID {}: {}", scopeDataArgs.getMerchant().getId(), lendingStateDTO);
        return lendingStateDTO;
    }

    @Override
    public LendingStateDTO<UpiAutopayStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        log.info("Fetching UPI Autopay Stage Data for Merchant ID: {}", scopeDataArgs.getMerchant().getId());
        UpiAutopayStateDTO upiAutopayStateDTO = new UpiAutopayStateDTO();
        upiAutopayStateDTO.setMerchantId(scopeDataArgs.getMerchant().getId());
        boolean isActiveApplicationAutoPaySetupFlow = isActiveApplicationAutoPaySetupFlow(scopeDataArgs);
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
        upiAutopayStateDTO.setDailyInstalmentAmount(lendingApplication.getEdi());
        LoanApplicationDetailsV3 loanApplicationDetails = setApplicationDetails(lendingApplication, isActiveApplicationAutoPaySetupFlow);
        upiAutopayStateDTO.setLoanApplication(loanApplicationDetails);

        upiAutopayStateDTO.setBankDetails(loanUtil.getAccountDetails(scopeDataArgs.getMerchant().getId()));
        upiAutopayStateDTO.setMaxMandateAmount(isActiveApplicationAutoPaySetupFlow ? loanUtil.getMaxMandateAmount(lendingApplication.getMerchantId()) : null);

        log.info("Setting if Nach is eligible for application: {}", lendingApplication.getId());
        if(!isActiveApplicationAutoPaySetupFlow){
            loanUtil.updateMandateColumnsInLAD(lendingApplicationDetails, lendingApplication.getLender(),
                    lendingApplication.getLoanAmount()
            );
        }

        AutoPayUPI existingAutoPayUpiWithMerchantId = autoPayUPIDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(lendingApplication.getMerchantId(),AutoPayStatusEnum.ACTIVE.name());
        log.info("Existing AutoPay UPI for Merchant ID {}: {}", lendingApplication.getMerchantId(), existingAutoPayUpiWithMerchantId);

        boolean autoPayUpiSkipped= autoPayUPIService.isEligibleForUpiAutoPaySkip(lendingApplication, existingAutoPayUpiWithMerchantId);
        AutoPayUPI existingAutoPayUPI = null;
        if(AUTO_PAY_UPI_APPLICABLE_LOAN_TYPES.contains(lendingApplication.getLoanType()) && autoPayUpiSkipped){
            existingAutoPayUPI = autoPayUPIService.cloneAutoPayUpiEntityForNewApplication(existingAutoPayUpiWithMerchantId, lendingApplication.getId());
            log.info("AutoPay UPI skipped for merchantId : {}", lendingApplication.getMerchantId());
            lendingApplication.setUpiAutopayStatus(UpiAutoPayStatus.APPROVED.name());
            lendingApplicationDao.save(lendingApplication);
            log.info("Lending application updated with UPI Autopay status: APPROVED for topup application ID: {}", lendingApplication.getId());
            loanDashboardService.deleteLoanDashboardCache(lendingApplication.getMerchantId());
        }

        existingAutoPayUPI = Optional.ofNullable(existingAutoPayUPI).orElse(autoPayUPIDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId()));
        log.info("Autopay Upi Mandate found for Application Id: {} : {}", lendingApplication.getId(), existingAutoPayUPI);

        // If the existing AutoPay UPI is not null, check its status and set the mandate status accordingly
        if (Objects.nonNull(existingAutoPayUPI)) {
            if (AutoPayStatusEnum.PENDING.equals(existingAutoPayUPI.getStatus()))
            {
                final MandateUPIStatusResponse mandateUPIStatusResponse = autoPayUPIService.checkStatus(scopeDataArgs.getMerchant(), existingAutoPayUPI.getOrderId());
                upiAutopayStateDTO.setMandateStatus(PaymentConstants.UPI_AUTOPAY_MANDATE_STATUS_MAP.getOrDefault(mandateUPIStatusResponse.data.getStatus(), mandateUPIStatusResponse.data.getStatus().name()));
                upiAutopayStateDTO.setMandateEndDate(existingAutoPayUPI.getMandateEndDate());
            } else if(AutoPayStatusEnum.FAILED.equals(existingAutoPayUPI.getStatus())){
                upiAutopayStateDTO.setMandateStatus(PaymentConstants.UPI_AUTOPAY_MANDATE_STATUS_MAP.getOrDefault(existingAutoPayUPI.getStatus(), existingAutoPayUPI.getStatus().name()));
                upiAutopayStateDTO.setMandateEndDate(existingAutoPayUPI.getMandateEndDate());
            } else {
                upiAutopayStateDTO.setMandateStatus(PaymentConstants.UPI_AUTOPAY_MANDATE_STATUS_MAP.getOrDefault(existingAutoPayUPI.getStatus(), existingAutoPayUPI.getStatus().name()));
                if(PaymentConstants.SUCCESS.equals(upiAutopayStateDTO.getMandateStatus()) && existingAutoPayUPI.isStandaloneAutopaySetup()) {
                    pushRemoveEvent(scopeDataArgs.getMerchant().getId());
                }
            }
            upiAutopayStateDTO.setCreatedAt(existingAutoPayUPI.getCreatedAt().getTime());
            upiAutopayStateDTO.setRetryCount(0);
        }
        else{
            upiAutopayStateDTO.setMandateStatus(PaymentConstants.INIT);
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
                upiAutopayStateDTO.setAutoPaySuccessNextState(LendingViewStates.UPI_AUTOPAY_PAGE);
                return new LendingStateDTO<>(upiAutopayStateDTO , LendingViewStates.UPI_AUTOPAY_PAGE, LendingViewStates.UPI_AUTOPAY_PAGE);
            }
            // upi mandate success case next page handling
            LendingViewStates nextState = null;
            boolean mandateTaskCompleted = true;
            if(MandateType.BOTH.equals(lendingApplicationDetails.getMandateType())){
                mandateTaskCompleted = isNachAlreadyDone(lendingApplication, lendingApplicationDetails);
            }

            if(mandateTaskCompleted){
                upiAutoPayStageHelper.invokeDocUploadFlow(lendingApplication, lendingApplicationDetails);
                if(LoanType.TOPUP.name().equals(lendingApplication.getLoanType())){
                    nextState = LendingViewStates.AGREEMENT_PAGE;
                }else {
                    nextState = vKycService.getLenderVkycPageOrDefault(LendingViewStates.APPLICATION_STATUS_PAGE,
                            lendingApplication.getMerchantId(), lendingApplication.getLender(), false);
                }
            }else {
                nextState = LendingViewStates.ENACH_PAGE;
            }
            upiAutopayStateDTO.setAutoPaySuccessNextState(nextState);
            loanDetailsV3Service.saveApplicationViewState(lendingApplicationDetails, lendingApplication.getId(), nextState);
        }

        log.info("Upi Autopay Stage Response for {} : {}", scopeDataArgs.getMerchant().getId(), upiAutopayStateDTO);
        return new LendingStateDTO<>(upiAutopayStateDTO , LendingViewStates.UPI_AUTOPAY_PAGE, LendingViewStates.UPI_AUTOPAY_PAGE);
    }

    private boolean isNachAlreadyDone(@NotNull LendingApplication lendingApplication, @NotNull LendingApplicationDetails lendingApplicationDetails) {
        boolean eligibleForNachSkip = loanUtil.isEligibleForNachSkip(lendingApplication, lendingApplication.getLender(), false);
        if(eligibleForNachSkip){
            log.info("nach skip is eligible for merchantId: {} and applicationId: {}", lendingApplication.getMerchantId(), lendingApplication.getId());
            lendingApplication.setNachStatus(NachStatus.APPROVED.name());
            lendingApplication.setNachType(NACH_TYPE);
            lendingApplication.setNachLender(loanUtil.enachServiceLenderMapper(lendingApplication.getLender()));
            lendingApplicationDetails.setIsNachSkip(true);
            lendingApplicationDao.save(lendingApplication);
            loanDashboardService.deleteLoanDashboardCache(lendingApplication.getMerchantId());
        }else {
            log.info("nach skip ineligible for merchantId: {} and applicationId: {}", lendingApplication.getMerchantId(), lendingApplication.getId());
            lendingApplicationDetails.setIsNachSkip(false);
        }
        lendingApplicationDetailsDao.save(lendingApplicationDetails);
        return eligibleForNachSkip;
    }

    /**
     * @param scopeDataArgs
     * @return true in case of active application and non topup case --> migration cases else false
     */
    private boolean isActiveApplicationAutoPaySetupFlow(ScopeDataArgs scopeDataArgs) {
        LendingPaymentScheduleSlave lps = lendingPaymentScheduleDaoSlave.findByMerchantIdAndStatus(scopeDataArgs.getMerchant().getId(), Collections.singletonList(LoanStatus.ACTIVE.name()));
        if(Objects.isNull(scopeDataArgs.getApplicationId()) && Objects.nonNull(scopeDataArgs.getLoanDetailsV3Request()) && scopeDataArgs.getLoanDetailsV3Request().isAutoPayPending()){
            if(!mandateRegistrationHelper.isAutopayRequiredForActiveApplication(lps)){
                throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_ELIGIBLE_FOR_UPI_AUTO_PAY.getErrorCode(), LoanDetailExceptionEnum.APPLICATION_NOT_ELIGIBLE_FOR_UPI_AUTO_PAY.getErrorMessage());
            }
            scopeDataArgs.setApplicationId(lps.getApplicationId());
        }
        if(Objects.nonNull(lps) && LoanStatus.ACTIVE.name().equalsIgnoreCase(lps.getStatus())){
            LendingApplication lendingApplication = lendingApplicationDao.findInProgressLoanApplication(scopeDataArgs.getMerchant().getId());
            if(Objects.nonNull(lendingApplication) && LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())){
                log.info("topup case found for active application on upiautopay stage for merchantId: {}, active applicationId: {}, and topup applicationId: {}",
                        scopeDataArgs.getMerchant().getId(), lps.getApplicationId(), lendingApplication.getId());
                return false;
            }else {
                log.info("autopay migration case found on upiautopay stage for merchantId: {}, active applicationId: {}",
                        scopeDataArgs.getMerchant().getId(), lps.getApplicationId());
                return true;
            }
        }
        return false;
    }

    private void pushRemoveEvent(Long merchantId) {
        PushDataToHomepageCarouselDto pushDataToHomepageCarouselDto = new PushDataToHomepageCarouselDto();
        pushDataToHomepageCarouselDto.setEvent_id("LENDING_HOMEPAGE_CAROUSEL_" + merchantId);
        pushDataToHomepageCarouselDto.setMerchant_id(BigInteger.valueOf(merchantId));
        pushDataToHomepageCarouselDto.setEvent_type("remove");
        pushDataToHomepageCarouselDto.setClient("LENDING");

        confluentKafkaProducer.sendMessage(pushDataToHomePageCarouselTopic, pushDataToHomepageCarouselDto);
        log.info("Sent remove event of cic banner for merchant {}", merchantId);

        BottomSheetEvent bottomSheetEvent = new BottomSheetEvent();
        bottomSheetEvent.setEventId("Lending_Auto_Pay_" + merchantId);
        bottomSheetEvent.setMerchantId(BigInteger.valueOf(merchantId));
        bottomSheetEvent.setEventType("remove");
        bottomSheetEvent.setClient("LENDING");

        confluentKafkaProducer.sendMessage(bottomSheetTopic, bottomSheetEvent);
        log.info("Sent remove event of bottom sheet for merchant {}", merchantId);
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
        return applicationDetails;
    }
}
