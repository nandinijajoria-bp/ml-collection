package com.bharatpe.lending.loanV3.revamp.scopes;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingAuditTrial;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LenderDisbursalLimitsDao;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingAuditTrialDao;
import com.bharatpe.lending.dto.ExperimentConfigResponseDTO;
import com.bharatpe.lending.entity.LendingLenderQuota;
import com.bharatpe.lending.enums.ApplicationStatus;
import com.bharatpe.lending.handlers.LaunchLabsHandler;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.dto.LenderAggregationResponseDto;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.bharatpe.lending.loanV3.revamp.util.LoanUtilV3;
import com.bharatpe.lending.service.impl.LenderAssignService;
import com.bharatpe.lending.util.CommonUtil;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class LenderAggregationStageService implements IStageDataService<LenderAggregationResponseDto>{

    @Autowired
    LendingAuditTrialDao lendingAuditTrialDao;

    @Autowired
    LenderDisbursalLimitsDao lenderDisbursalLimitsDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LoanDetailsV3Service loanDetailsV3Service;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    @Lazy
    LendingApplicationServiceV2 lendingApplicationServiceV2;


    @Value("${aggregation.flow.experimentId:37}")
    String isAggregationFlowApplicableExperimentId;

    @Value("${lender.assign.threshold}")
    Integer maxLenderAssignThreshold;

    @Autowired
    CommonUtil commonUtil;

    @Autowired
    LenderAssignService lenderAssignService;

    @Lazy
    @Autowired
    LoanUtilV3 loanUtilV3;

    @Override
    public LendingStateDTO<LenderAggregationResponseDto> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        LendingApplication lendingApplication = lendingApplicationDao.findByMerchantIdAndStatus(scopeDataArgs.getMerchant().getId(), "draft");
        if (ObjectUtils.isEmpty(lendingApplication)) {
            log.info("appplication not found for merchant:{}", scopeDataArgs.getMerchant().getId());
            return new LendingStateDTO<>(null, LendingViewStates.LENDER_AGGREGATION, LendingViewStates.LENDER_AGGREGATION);
        }
        List<String> prevlenders = lendingApplicationLenderDetailsDao.findPreviousLenders(lendingApplication.getId());
        if (!ObjectUtils.isEmpty(prevlenders) && prevlenders.size() >= maxLenderAssignThreshold){
            log.info("rejecting application {} as max attempts to choose lender breached", lendingApplication.getId());
            lendingApplication.setStatus(ApplicationStatus.REJECTED.name().toLowerCase());
            lendingApplicationDao.save(lendingApplication);
            lendingApplicationServiceV2.evictCache(scopeDataArgs.getMerchant().getId());
            return new LendingStateDTO<>(null, LendingViewStates.LENDER_AGGREGATION, LendingViewStates.LENDER_AGGREGATION);
        }
        LendingLenderQuota defaultLender = lenderDisbursalLimitsDao.findByEdiModelIsNull();
        if(!ObjectUtils.isEmpty(defaultLender) && defaultLender.getLender().equals(lendingApplication.getLender())){
            log.info("rejecting application {} as default lender rejected the application", lendingApplication.getId());
            lendingApplication.setStatus(ApplicationStatus.REJECTED.name().toLowerCase());
            lendingApplicationDao.save(lendingApplication);
            lendingApplicationServiceV2.evictCache(scopeDataArgs.getMerchant().getId());
            return new LendingStateDTO<>(null, LendingViewStates.LENDER_AGGREGATION, LendingViewStates.LENDER_AGGREGATION);
        }
        List<LenderAggregationResponseDto.LenderData> lenderData = new ArrayList<>();
        log.info("Attempt no.{} to choose lender for lendingApplication:{}", Objects.nonNull(prevlenders) ? prevlenders.size() + 1 : 1, lendingApplication.getId());
        lenderData = lenderAssignService.getEligibleLenderList(lendingApplication, prevlenders);
        if(ObjectUtils.isEmpty(lenderData) || shouldApplicationBeRejected(lenderData)){
            commonUtil.saveApplicationRejectionAudit(lendingApplication, ApplicationStatus.REJECTED.name().toLowerCase(), lendingApplication.getStatus(),"APP_STATUS","rejected due to nullable lender");
            lendingApplication.setStatus(ApplicationStatus.REJECTED.name().toLowerCase());
            lendingApplication.setManualKyc(ApplicationStatus.REJECTED.name().toLowerCase());
            lendingApplication.setManualKycReason("NONE_ELIGIBLE_LENDER");
            lendingApplicationDao.save(lendingApplication);
            lendingApplicationServiceV2.evictCache(lendingApplication.getMerchantId());
        }
        LenderAggregationResponseDto responseDto = new LenderAggregationResponseDto();
        responseDto.setLenders(lenderData);
        responseDto.setEdi(lendingApplication.getEdi().intValue());
        responseDto.setApplicationId(lendingApplication.getId());
        responseDto.setTenure(lendingApplication.getTenure());
        responseDto.setLoanAmount(lendingApplication.getLoanAmount());
        responseDto.setProcessingFee(lendingApplication.getProcessingFee());
        responseDto.setInterestRate(lendingApplication.getInterestRate());
        responseDto.setLoanType(lendingApplication.getLoanType());
        responseDto.setPreviousLender(lendingApplication.getLender());
        responseDto.setRepeatLoan(loanUtil.isRepeatLoan(lendingApplication.getMerchantId()));
        responseDto.setScreenType(loanUtil.getLenderAggregationScreen(lendingApplication.getId()));
        responseDto.setAttemptCount(prevlenders.size() + 1);
        responseDto.setMessage(getMessage(responseDto.getAttemptCount(), lendingApplication.getLender(), Objects.nonNull(defaultLender) ? defaultLender.getLender() : null));


        loanDetailsV3Service.saveApplicationViewState(null, lendingApplication.getId(), LendingViewStates.LENDER_AGGREGATION);
        return new LendingStateDTO<>(responseDto, LendingViewStates.SHOP_DETAILS_PAGE, LendingViewStates.LENDER_AGGREGATION);
    }

    @Override
    public LendingStateDTO<LenderAggregationResponseDto> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<LenderAggregationResponseDto> lendingStateDTO = fetchScopedData(scopeDataArgs);
        if(!ObjectUtils.isEmpty(lendingStateDTO.getData()) &&  loanUtilV3.isPreapprovedRepeatLoan(lendingStateDTO.getData().getApplicationId())) {
            lendingStateDTO.setLendingViewStates(LendingViewStates.KYC_PAGE);
        }
        return lendingStateDTO;
    }

    public String getMessage(Integer attempts, String assignedLender, String defaultLender) {
        if (attempts < maxLenderAssignThreshold && attempts > 1) {
            return LendingConstants.LENDER_REJECTION_MESSAGE1.replace("{{lenderName}}", assignedLender);
        } else if (attempts >= maxLenderAssignThreshold && Objects.nonNull(defaultLender)) {
            return LendingConstants.LENDER_REJECTION_MESSAGE2.replace("{{defaultLender}}", defaultLender).replace("{{LenderName}}", assignedLender);
        }
        return null;
    }

    private Boolean shouldApplicationBeRejected(List<LenderAggregationResponseDto.LenderData> lenderData){
        if(!CollectionUtils.isEmpty(lenderData)) {
            for(LenderAggregationResponseDto.LenderData lender : lenderData){
                if(Boolean.FALSE.equals(lender.getRejected())){
                    return false;
                }
            }
        }
        return true;
    }
}