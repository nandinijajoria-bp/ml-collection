package com.bharatpe.lending.loanV3.services.associations.piramal;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactoryV2;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.bharatpe.lending.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Optional;

@Service
@Slf4j
public class CommonService {

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Lazy
    @Autowired
    NbfcUtils nbfcUtils;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    CommonUtil commonUtil;

    @Lazy
    @Autowired
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Autowired
    LendingLenderPricingDao lendingLenderPricingDao;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;
    @Autowired
    private LendingEligibleLoanDao eligibleLoanDao;
    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    PricingExperimentDao pricingExperimentDao;

    @Value("${pricing.experiment.enable:false}")
    boolean pricingExpEnabled;

    public void manageApplicationState(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        if (lenderAssociationDetailsDto.isManageState()) {
            log.info("setting stage manageApplicationState {}", lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getStage());
            lenderAssociationDetailsDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetailsDao.save(lenderAssociationDetailsDto.getLendingApplicationLenderDetails()));
        }
    }

    public void modfifyApplicationLender(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto, LenderAssociationStatus lenderAssociationStatus) {
        log.info("request for modify lender received for {}", lenderAssociationDetailsDto.getApplicationId());
        if (lenderAssociationDetailsDto.isModifyLender()) {
            log.info("modifying lender !");
            nbfcUtils.modifyLender(lenderAssociationDetailsDto.getLendingApplication(), lenderAssociationDetailsDto.getLendingApplicationLenderDetails(), lenderAssociationStatus);
        }
    }

    public void manageApplicationStateAndModifyLender(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto, LenderAssociationStatus lenderAssociationStatus) {
        manageApplicationState(lenderAssociationDetailsDto);
        if (LoanType.TOPUP.name().equalsIgnoreCase(lenderAssociationDetailsDto.getLendingApplication().getLoanType()) && Arrays.asList(Lender.TRILLIONLOANS.name(), Lender.PIRAMAL.name()).contains(lenderAssociationDetailsDto.getLendingApplication().getLender()))
            manageApplicationStateAndRejectApplication(lenderAssociationDetailsDto);
        else
            modfifyApplicationLender(lenderAssociationDetailsDto, lenderAssociationStatus);
    }

    public void manageApplicationStateAndPushToNextStage(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        String currStage = lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getStage();
        LenderAssociationStages nextStage =
                LenderAssociationStageFactoryV2.getNextStage(Lender.valueOf(lenderAssociationDetailsRequest.getLendingApplication().getLender()),
                        LenderAssociationStages.valueOf(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getStage()));
        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setStage(nextStage.name());
        manageApplicationState(lenderAssociationDetailsRequest);
        nbfcUtils.pushApplicationToNextStage(lenderAssociationDetailsRequest.getApplicationId(),
                lenderAssociationDetailsRequest.getLendingApplication().getLender(),
                currStage,
                LenderAssociationStageFactoryV2.autoInvokeNextStage(Lender.valueOf(lenderAssociationDetailsRequest.getLendingApplication().getLender()), LenderAssociationStages.valueOf(currStage)));
    }

    public void manageApplicationStateAndRejectApplication(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        manageApplicationState(lenderAssociationDetailsRequest);
        rejectApplication(lenderAssociationDetailsRequest.getLendingApplication(), lenderAssociationDetailsRequest.getLendingApplicationLenderDetails());
    }

    public void rejectApplication(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails) {
        String rejectReason = lendingApplication.getLender() + "_" + lendingApplicationLenderDetails.getLeadStatus();
        log.info("rejecting application due to {} for applicationId {} with rejectReason {}", lendingApplicationLenderDetails.getLeadStatus(), lendingApplication.getId(), rejectReason);
        if (!ObjectUtils.isEmpty(lendingApplication)) {
            String oldStatus = lendingApplication.getStatus();
            lendingApplication.setStatus("rejected");
            lendingApplication.setManualKyc("rejected");
            lendingApplication.setManualKycReason(rejectReason);
            lendingApplicationDao.save(lendingApplication);
            lendingApplicationServiceV2.evictCache(lendingApplication.getMerchantId());
            commonUtil.saveApplicationRejectionAudit(lendingApplication, "rejected", oldStatus, "APP_STATUS", lendingApplication.getManualKyc());
        }
        if (!ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            lendingApplicationLenderDetails.setStatus(Status.INACTIVE.name());
            lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
        }
    }

    public boolean additionalLenderDowngradeChecksFailed(LendingApplication lendingApplication, String lender){
        boolean result = false;
        result = nbfcUtils.additionalLenderDowngradeChecksFailed(lendingApplication);
        return result;
    }

    public LendingApplication createDuplicateApplication(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails){
        LendingApplication newApplication = new LendingApplication();
        BeanUtils.copyProperties(lendingApplication, newApplication);
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
        PricingExperiment pricingExperiment = pricingExperimentDao.findBySegmentAndRiskGroupAndMidEndsWithAndPincodeColor(lendingRiskVariablesSnapshot.getRiskSegment().name(),
                lendingRiskVariablesSnapshot.getRiskGroup(),
                (int) (lendingApplication.getMerchantId()%10),
                lendingRiskVariablesSnapshot.getPincodeColor().name(),
                lendingApplication.getCreatedAt()
        );

        log.info("Requested loan amount : {}", lendingApplication.getLoanAmount());
        Double loanAmount = lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt();
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findByApplicationId(lendingApplication.getId());
        Optional<LendingEligibleLoan> eligibleLoan = eligibleLoanDao.findById(lendingApplicationDetails.getOfferId());

        Double pfRate;
        if(pricingExpEnabled && !ObjectUtils.isEmpty(pricingExperiment)) {
            log.info("experiment available for {}", pricingExperiment);
            pfRate = pricingExperiment.getProcessingFeeRate();
        }else {
            LendingLenderPricing lendingLenderPricing = lendingLenderPricingDao.findBySegmentAndRiskGroupAndTenureInMonthsAndLenderAndPincodeColor(
                    lendingRiskVariablesSnapshot.getRiskSegment().name(),
                    lendingRiskVariablesSnapshot.getRiskGroup(),
                    lendingApplication.getTenureInMonths(),
                    lendingApplication.getLender(),
                    lendingRiskVariablesSnapshot.getPincodeColor().name(),
                    lendingApplication.getCreatedAt()
            );

            if(ObjectUtils.isEmpty(lendingLenderPricing)){
                log.info("Lending lender pricing not available, using eligible loan values");
                pfRate = eligibleLoan.get().getProcessingFeeRate();
            } else {
                pfRate = lendingLenderPricing.getProcessingFeeRate();
            }
        }

        Double processingFee = Math.ceil((pfRate * loanAmount) / 100);
        Double interestAmt = (loanAmount * (lendingApplication.getInterestRate() * lendingApplication.getTenureInMonths()) / 100) ;
        Double ediAmount = Math.ceil((loanAmount + interestAmt) / lendingApplication.getPayableDays());
        newApplication.setLoanAmount(loanAmount);
        newApplication.setProcessingFee(processingFee);
        newApplication.setEdi(ediAmount);
        return newApplication;
    }

    public boolean offerDowngradeThresholdChecksFailed(double offerDowngradeThreshold, LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto){
        return nbfcUtils.offerDowngradeThresholdChecksFailed(offerDowngradeThreshold, lenderAssociationDetailsRequestDto);
    }
}
