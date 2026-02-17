package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Constants.AutoPayStatusEnum;
import com.bharatpe.lending.common.dao.AutoPayUPIDao;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.AutoPayUPI;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.MandateType;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.lendingplatform.lms.constant.Constants;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.NachStatus;
import com.bharatpe.lending.loanV3.revamp.enums.UpiAutoPayStatus;
import com.bharatpe.lending.loanV3.services.VKycService;
import com.bharatpe.lending.service.AutoPayUPIService;
import com.bharatpe.lending.service.PostAgreementAsyncFlowService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpiAutoPayStageHelper {

    private final LoanUtil loanUtil;
    private final EasyLoanUtil easyLoanUtil;
    private final VKycService vKycService;
    private final AutoPayUPIDao autoPayUPIDao;
    private final AutoPayUPIService autoPayUPIService;
    private final LendingApplicationDao lendingApplicationDao;
    private final LendingApplicationDetailsDao lendingApplicationDetailsDao;
    private final LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;
    private final PostAgreementAsyncFlowService postAgreementAsyncFlowService;

    @Value("${upi.autopay.retry.count:3}")
    private int upiAutoPayMaxRetryCount;

    @Value("${upi.autopay.force.skip.percentage:0}")
    private int upiAutoPayForceSkipPercentage;

    public boolean isEligibleForFailedForceSkip(Long applicationId, Long merchantId) {
        if(!easyLoanUtil.percentScaleUp(merchantId, upiAutoPayForceSkipPercentage)){
            return false;
        }
        Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(applicationId);
        if(lendingApplicationOptional.isPresent()){
            LendingApplication lendingApplication = lendingApplicationOptional.get();
            if(Constants.DISBURSED_LOAN.equalsIgnoreCase(lendingApplication.getLoanDisbursalStatus())){
                return false;
            }
        }
        if(isLowTpvMerchant(applicationId)){
            return false;
        }
        List<AutoPayUPI> autoPayUPIList = autoPayUPIDao.findAllByApplicationIdOrderByIdDesc(applicationId);
        if(CollectionUtils.isEmpty(autoPayUPIList)){
            return false;
        }
        AutoPayUPI latestAutoPayUpi = autoPayUPIList.get(0);
        if(!AutoPayStatusEnum.FAILED.equals(latestAutoPayUpi.getStatus())){
            log.info("Latest UPI AutoPay status is not FAILED for applicationId, orderId, and status are :{}, {}, {} ",
                    applicationId, latestAutoPayUpi.getOrderId(), latestAutoPayUpi.getStatus());
            return false;
        }
        List<AutoPayUPI> failedCases = autoPayUPIList.stream()
                .filter(autoPayUPI -> AutoPayStatusEnum.FAILED.equals(autoPayUPI.getStatus()))
                .collect(Collectors.toList());
        log.info("Total UPI AutoPay failed attempts for applicationId : {} are : {}", applicationId, failedCases.size());
        return failedCases.size() >= upiAutoPayMaxRetryCount;
    }

    private boolean isLowTpvMerchant(Long applicationId) {
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(applicationId);
        if(lendingRiskVariablesSnapshot==null){
            return false;
        }
        String pilotIdentifier = lendingRiskVariablesSnapshot.getPilotIdentifier();
        if(Objects.isNull(pilotIdentifier)){
            return false;
        }
        return LendingConstants.TPV_500_IDENTIFIERS.stream()
                .anyMatch(pilotIdentifier::contains);
    }

    public LendingViewStates forceSkipUpiAutopayAndGetNextPage(Long applicationId) {
        LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).get();
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(applicationId);
        return forceSkipUpiAutopayAndGetNextPage(lendingApplication, lendingApplicationDetails);
    }

    public LendingViewStates forceSkipUpiAutopayAndGetNextPage(@NotNull LendingApplication lendingApplication,
                                                               @NotNull LendingApplicationDetails lendingApplicationDetails) {

        log.info("Forcing skip UPI AutoPay for applicationId : {}, merchantId : {}",
                lendingApplication.getId(), lendingApplication.getMerchantId());
        boolean isEligibleForSkipNach = loanUtil.isEligibleForNachSkip(lendingApplication, lendingApplication.getLender(), false);
        log.info("isEligibleForSkipNach : {} for applicationId : {}", isEligibleForSkipNach, lendingApplication.getId());
        LendingViewStates nextPage;
        if(isEligibleForSkipNach){
            lendingApplication.setNachStatus(NachStatus.APPROVED.name());
            lendingApplication.setNachType("ENACH");
            lendingApplication.setNachLender(loanUtil.enachServiceLenderMapper(lendingApplication.getLender()));
            lendingApplicationDao.save(lendingApplication);
            if(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())){
                nextPage=LendingViewStates.AGREEMENT_PAGE;
            }else {
                nextPage = vKycService.getLenderVkycPageOrDefault(
                        LendingViewStates.APPLICATION_STATUS_PAGE, lendingApplication.getMerchantId(),
                        lendingApplication.getLender(), LoanType.TOPUP.name().equals(lendingApplication.getLoanType()));
                invokeDocUploadFlow(lendingApplication, lendingApplicationDetails);
            }
        }else {
            nextPage = LendingViewStates.ENACH_PAGE;
            lendingApplication.setNachStatus(null);
            lendingApplicationDetails.setIsNachSkip(false);
        }
        log.info("Next page after forcing skip UPI AutoPay for applicationId : {} is : {}", lendingApplication.getId(), nextPage);
        lendingApplicationDetails.setApplicationViewState(nextPage.name());
        lendingApplicationDetails.setAutoPayUpiEligible(false);
        lendingApplicationDetails.setNachEligible(true);
        lendingApplicationDetails.setMandateType(MandateType.AUTOPAY_NACH);
        lendingApplicationDetails.setMandateFlagsToggledOn(new Date());
        lendingApplicationDao.save(lendingApplication);
        lendingApplicationDetailsDao.save(lendingApplicationDetails);
        return nextPage;
    }

    public void invokeDocUploadFlow(@NotNull LendingApplication lendingApplication, @NotNull LendingApplicationDetails lendingApplicationDetails) {
        if(LoanType.TOPUP.name().equals(lendingApplication.getLoanType())){
            log.info("Loan type: TOPUP for application: {}, skipping this flow", lendingApplication.getId());
            return;
        }
        postAgreementAsyncFlowService.invokeNextStage(lendingApplication, Optional.empty(), Optional.of(lendingApplicationDetails));
    }

    public boolean checkSkipEligibilityAndUpdateUpiAutoPayStatus(@NotNull LendingApplication lendingApplication) {
        log.info("checking UPI AutoPay skip eligibility for merchantId: {} and applicationId: {}", lendingApplication.getMerchantId(), lendingApplication.getId());
        AutoPayUPI autoPayUPIExistingEntityMerchantId = autoPayUPIDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(lendingApplication.getMerchantId(),AutoPayStatusEnum.ACTIVE.name());
        boolean autoPayUpiSkipped= autoPayUPIService.isEligibleForUpiAutoPaySkip(lendingApplication,autoPayUPIExistingEntityMerchantId);
        if(autoPayUpiSkipped){
            autoPayUPIService.cloneAutoPayUpiEntityForNewApplication(autoPayUPIExistingEntityMerchantId, lendingApplication.getId());
            log.info("AutoPay UPI skipped for merchantId : {}", lendingApplication.getMerchantId());
            lendingApplication.setUpiAutopayStatus(UpiAutoPayStatus.APPROVED.name());
            lendingApplicationDao.save(lendingApplication);
            return true;
        }else {
            log.info("upiautopay is not eligible for skip for applicationId: {} and merchantId:{}",
                    lendingApplication.getId(), lendingApplication.getMerchantId());
            // this is bank change case
            if(UpiAutoPayStatus.APPROVED.name().equals(lendingApplication.getUpiAutopayStatus())){
                log.info("found upi autopay status as approved for applicationId: {} and merchantId:{} marking it as null",
                        lendingApplication.getId(), lendingApplication.getMerchantId());
                lendingApplication.setUpiAutopayStatus(null);
                lendingApplicationDao.save(lendingApplication);
            }
            return false;
        }
    }

}
