package com.bharatpe.lending.loanV3.revamp.services;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.enums.MandateType;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.revamp.enums.UpiAutoPayStatus;
import com.bharatpe.lending.loanV3.revamp.enums.NachStatus;
import com.bharatpe.lending.service.PostAgreementAsyncFlowService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnachStageHelper {

    private final PostAgreementAsyncFlowService postAgreementAsyncFlowService;
    private final LendingApplicationDao lendingApplicationDao;
    private final LendingApplicationDetailsDao lendingApplicationDetailsDao;
    private final LoanUtil loanUtil;

    public void processNachSuccessStatus(@NotNull Long applicationId) {
        LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).orElse(null);
        if (Objects.isNull(lendingApplication)) {
            log.error("Lending application not found for id: {}", applicationId);
            return;
        }
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findByApplicationId(applicationId);
        if (Objects.isNull(lendingApplicationDetails)) {
            log.error("Lending application details not found while processing nach success for application id: {}", applicationId);
            return;
        }
        processNachSuccessStatus(lendingApplication, lendingApplicationDetails);
    }

    public void processNachSuccessStatus(@NotNull LendingApplication lendingApplication, @NotNull LendingApplicationDetails lendingApplicationDetails){
        if(MandateType.DIGIO_UPI.equals(lendingApplicationDetails.getMandateType()) && lendingApplicationDetails.isAutoPayUpiEligible()){
            lendingApplication.setUpiAutopayStatus(UpiAutoPayStatus.APPROVED.name());
        }else {
            lendingApplication.setNachStatus(NachStatus.APPROVED.name());
        }
        lendingApplication.setNachType(LendingConstants.NACH_TYPE);
        lendingApplication.setNachLender(loanUtil.enachServiceLenderMapper(lendingApplication.getLender()));
        lendingApplicationDao.save(lendingApplication);
        if(LoanType.TOPUP.name().equals(lendingApplication.getLoanType())){
            log.info("skipping next stage invoke after nach completion for topup and application: {}", lendingApplication.getId());
            return;
        }
        postAgreementAsyncFlowService.invokeNextStage(lendingApplication, Optional.empty(), Optional.of(lendingApplicationDetails));
    }

    public boolean checkNachSkipEligibilityAndUpdateStatus(@NotNull LendingApplication lendingApplication, @NotNull LendingApplicationDetails lendingApplicationDetails) {
        log.info("checking nach skip eligibility for merchantId: {} and applicationId: {}", lendingApplication.getMerchantId(), lendingApplication.getId());
        boolean eligibleForNachSkip = loanUtil.isEligibleForNachSkip(lendingApplication, lendingApplication.getLender(), false);
        if(eligibleForNachSkip){
            log.info("nach skip is eligible for merchantId: {} and applicationId: {}", lendingApplication.getMerchantId(), lendingApplication.getId());
            lendingApplication.setNachStatus(NachStatus.APPROVED.name());
            lendingApplication.setNachType("ENACH");
            lendingApplication.setNachLender(loanUtil.enachServiceLenderMapper(lendingApplication.getLender()));
            lendingApplicationDetails.setIsNachSkip(true);
            lendingApplicationDao.save(lendingApplication);
        }else {
            log.info("nach skip ineligible for merchantId: {} and applicationId: {}", lendingApplication.getMerchantId(), lendingApplication.getId());
            lendingApplicationDetails.setIsNachSkip(false);
        }
        lendingApplicationDetailsDao.save(lendingApplicationDetails);
        return eligibleForNachSkip;
    }
}
