package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Constants.AutoPayStatusEnum;
import com.bharatpe.lending.common.dao.AutoPayUPIDao;
import com.bharatpe.lending.common.entity.AutoPayUPI;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.PaymentConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.MandateUPIStatusResponse;
import com.bharatpe.lending.enums.ApplicationStatus;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.revamp.dto.*;
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
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;

@Service
@Slf4j
public class UpiAutopayStageService implements IStageDataService<UpiAutopayStateDTO>{

    public static final String REGULAR = "REGULAR";
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
    AutoPayUPIService autoPayUPIService;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LoanDashboardService loanDashboardService;


    @Override
    public LendingStateDTO<UpiAutopayStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        log.info("Processing UPI Autopay Stage for Merchant ID: {}", scopeDataArgs.getMerchant().getId());

        // Fetching the lending state data for the current scope
        LendingStateDTO<UpiAutopayStateDTO> lendingStateDTO = fetchScopedData(scopeDataArgs);
        log.info("Lending State DTO after fetching scoped data: {}", lendingStateDTO);

        // If the mandate status is IN_PROGRESS or FAILED, set the view state to UPI_AUTOPAY_PAGE
        if(!PaymentConstants.SUCCESS.equals(lendingStateDTO.getData().getMandateStatus())) {
            lendingStateDTO.setLendingViewStates(LendingViewStates.UPI_AUTOPAY_PAGE);
            return lendingStateDTO;
        }

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
        log.info("Final Lending State DTO for Merchant ID {}: {}", scopeDataArgs.getMerchant().getId(), lendingStateDTO);
        return lendingStateDTO;
    }

    @Override
    public LendingStateDTO<UpiAutopayStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        log.info("Fetching UPI Autopay Stage Data for Merchant ID: {}", scopeDataArgs.getMerchant().getId());
        UpiAutopayStateDTO upiAutopayStateDTO = new UpiAutopayStateDTO();
        upiAutopayStateDTO.setMerchantId(scopeDataArgs.getMerchant().getId());
        upiAutopayStateDTO.setApplicationId(scopeDataArgs.getApplicationId());

        LendingApplication lendingApplication = lendingApplicationServiceV3.getLendingApplication(scopeDataArgs.getApplicationId(),scopeDataArgs.getMerchant().getId());
        if (ObjectUtils.isEmpty(lendingApplication)) {
            log.info("Application not found for {}", scopeDataArgs.getMerchant().getId());
            throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorMessage());
        }
        log.info("Lending Application for Merchant ID {}: {}", scopeDataArgs.getMerchant().getId(), lendingApplication);

        upiAutopayStateDTO.setLender(lendingApplication.getLender());
        upiAutopayStateDTO.setLoanAmount(lendingApplication.getLoanAmount());
        upiAutopayStateDTO.setTenure(lendingApplication.getTenureInMonths());
        upiAutopayStateDTO.setLoanType(lendingApplication.getLoanType());
        upiAutopayStateDTO.setWaitTime(upiAutopayProgressWaitTime);
        upiAutopayStateDTO.setPollingTime(upiAutopayStatusPollingInterval);
        LoanApplicationDetailsV3 loanApplicationDetails = setApplicationDetails(lendingApplication);
        upiAutopayStateDTO.setLoanApplication(loanApplicationDetails);

        upiAutopayStateDTO.setBankDetails(loanUtil.getAccountDetails(scopeDataArgs.getMerchant().getId()));

        AutoPayUPI existingAutoPayUpiWithMerchantId = autoPayUPIDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(lendingApplication.getMerchantId(),"ACTIVE");
        log.info("Existing AutoPay UPI for Merchant ID {}: {}", lendingApplication.getMerchantId(), existingAutoPayUpiWithMerchantId);

        boolean autoPayEligible= nonRegularMandateRegistrationChecks(lendingApplication, existingAutoPayUpiWithMerchantId);
        log.info("AutoPay eligibility for Merchant ID {}: {}", lendingApplication.getMerchantId(), autoPayEligible);

        if(Arrays.asList(REGULAR, "TOPUP").contains(lendingApplication.getLoanType()) && !autoPayEligible && existingAutoPayUpiWithMerchantId != null){
            existingAutoPayUpiWithMerchantId.setApplicationId(lendingApplication.getId());
            autoPayUPIDao.save(existingAutoPayUpiWithMerchantId);
            log.info("AutoPay UPI updated with new application ID: {}", lendingApplication.getId());
            lendingApplication.setUpiAutopayStatus("APPROVED");
            lendingApplicationDao.save(lendingApplication);
            log.info("Lending application updated with UPI Autopay status: APPROVED for topup application ID: {}", lendingApplication.getId());
        }
        if(REGULAR.equalsIgnoreCase(lendingApplication.getLoanType()) && existingAutoPayUpiWithMerchantId != null && autoPayEligible){
            // revoke previous mandate
            autoPayUPIService.revokeMandate(lendingApplication, existingAutoPayUpiWithMerchantId);
        }

        AutoPayUPI existingAutoPayUPI = autoPayUPIDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId());
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

        log.info("Autopay UPI Mandate status for application: {}: {}", lendingApplication.getId(), upiAutopayStateDTO.getMandateStatus());

        if(Objects.nonNull(upiAutopayStateDTO.getMandateStatus()) && PaymentConstants.SUCCESS.equals(upiAutopayStateDTO.getMandateStatus())){
            // TODO: Next stage will change based on config, so we need to update this logic.
            loanDetailsV3Service.saveApplicationViewState(null, lendingApplication.getId(), LendingViewStates.ENACH_PAGE);
        }
        else{
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

    private boolean nonRegularMandateRegistrationChecks(LendingApplication lendingApplication, AutoPayUPI autoPayUPIExistingEntityMerchantId) {
        log.info("Performing non-regular mandate registration checks for application id: {}", lendingApplication.getId());
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
        log.info("Setting application details for open application: {}", openApplication.getId());
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
