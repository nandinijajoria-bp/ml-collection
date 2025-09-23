package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.Constants.AutoPayStatusEnum;
import com.bharatpe.lending.common.dao.AutoPayUPIDao;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.AutoPayUPI;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.MandateType;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.lendingplatform.lending.service.VerifyOTPServiceV2;
import com.bharatpe.lending.lendingplatform.lms.constant.Constants;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactoryV2;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.NachStatus;
import com.bharatpe.lending.loanV3.services.VKycService;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.bharatpe.lending.util.LoanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpiAutoPayStageHelper {

    private final LoanUtil loanUtil;

    @Lazy
    private final NbfcUtils nbfcUtils;
    private final EasyLoanUtil easyLoanUtil;
    private final VKycService vKycService;
    private final AutoPayUPIDao autoPayUPIDao;
    private final VerifyOTPServiceV2 verifyOTPServiceV2;
    private final LendingApplicationDao lendingApplicationDao;
    private final LendingApplicationDetailsDao lendingApplicationDetailsDao;
    private final LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;
    private final LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

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
            nextPage = vKycService.getLenderVkycPageOrDefault(
                    LendingViewStates.APPLICATION_STATUS_PAGE, lendingApplication.getMerchantId(),
                    lendingApplication.getLender(), LoanType.TOPUP.name().equals(lendingApplication.getLoanType()));
            invokeDocUploadFlow(lendingApplication);
            lendingApplication.setNachStatus(NachStatus.APPROVED.name());
            lendingApplication.setNachType("ENACH");
            lendingApplication.setNachLender(loanUtil.enachServiceLenderMapper(lendingApplication.getLender()));
            lendingApplicationDao.save(lendingApplication);
        }else {
            nextPage = LendingViewStates.ENACH_PAGE;
            lendingApplication.setNachStatus(null);
            lendingApplicationDetails.setIsNachSkip(false);
        }
        log.info("Next page after forcing skip UPI AutoPay for applicationId : {} is : {}", lendingApplication.getId(), nextPage);
        lendingApplicationDetails.setApplicationViewState(nextPage.name());
        lendingApplicationDetails.setAutoPayUpiEligible(false);
        lendingApplicationDetails.setNachEligible(true);
        Map<String, Object> metadata = Objects.isNull(lendingApplicationDetails.getMetaData()) ? new HashMap<>() : lendingApplicationDetails.getMetaData();
        metadata.put(LendingConstants.MANDATE_TYPE_KEY, MandateType.AUTOPAY_NACH);
        lendingApplicationDetails.setMetaData(metadata);
        lendingApplicationDetails.setMandateFlagsToggledOn(new Date());
        lendingApplicationDao.save(lendingApplication);
        lendingApplicationDetailsDao.save(lendingApplicationDetails);
        return nextPage;
    }

    public void invokeDocUploadFlow(@NotNull LendingApplication lendingApplication) {
        if(LoanType.TOPUP.name().equals(lendingApplication.getLoanType())){
            log.info("Loan type: TOPUP for application: {}, skipping this flow", lendingApplication.getId());
            return;
        }
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(
                lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
        log.info("Lending Application Lender Details for application {}: {}", lendingApplication.getId(), lendingApplicationLenderDetails);

        if(ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.info("No lending application lender details found for application id: {}", lendingApplication.getId());
            return;
        }
        if( ! LenderAssociationStages.ASSC_COMPLETED.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage())){
            log.info("Application is not in ASSC_COMPLETED stage for applicationId: {}, merchantId: {}, current stage is: {}, skipping this doc upload flow",
                    lendingApplication.getId(), lendingApplication.getMerchantId(), lendingApplicationLenderDetails.getStage());
            return;
        }
        if(BooleanUtils.isTrue(lendingApplicationLenderDetails.getRearchFlow())){
            log.info("Rearch flow is enabled for application id: {}, invoking doc upload workflow", lendingApplication.getId());
            verifyOTPServiceV2.invokeDocUploadAndNachWorflow(lendingApplication);
        }
        else {
            log.info("Rearch flow is not enabled for application id: {}, pushing application to next stage", lendingApplication.getId());
            nbfcUtils.pushApplicationToNextStage(lendingApplication.getId(), lendingApplication.getLender(), LenderAssociationStages.ASSC_COMPLETED.name(),
                    LenderAssociationStageFactoryV2.autoInvokeNextStage(Lender.valueOf(lendingApplication.getLender()), LenderAssociationStages.ASSC_COMPLETED));
            log.info("invoked doc upload workflow of {} for application {} since NACH is skipped for  merchanId {}", lendingApplication.getLender(), lendingApplication.getId(), lendingApplication.getMerchantId());
        }
    }
}
