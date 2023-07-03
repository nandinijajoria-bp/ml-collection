package com.bharatpe.lending.loanV3.services.associationsV2.piramal.wrapper;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl.RiskDecisionSyncService;
import com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl.UpdateLeadService;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import io.netty.util.internal.ObjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
public class UpdateLeadAndRiskDecisionWrapperService {

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    CommonService commonService;

    @Autowired
    UpdateLeadService updateLeadService;

    @Autowired
    RiskDecisionSyncService riskDecisionSyncService;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    NbfcUtils nbfcUtils;

    @Value("${lender.change.enabled:false}")
    private Boolean enableLenderChange;

    @Async("piramalPoolTaskExecutor")
    public void invokeUpdateLeadAndRiskDecisionWorkflow(Map<String,String> request, Map<String, Object> args) {
        if (null != args && args.containsKey("requestId")) {
            MDC.put("requestId", (String) args.get("requestId"));
        }
        Long applicationId = Long.valueOf(request.get("application_id"));
        if (ObjectUtils.isEmpty(applicationId)) {
            log.info("No application id found");
            MDC.clear();
            return;
        }
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto();
        createRecord(lenderAssociationDetailsDto, applicationId);
        if (Objects.isNull(lenderAssociationDetailsDto.getLendingApplicationLenderDetails())) {
            log.info("lead creation was unsuccessful for {}", applicationId);
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.UPDATE_LEAD_FAILED.name());
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.UPDATE_LEAD_FAILED);
            MDC.clear();
            return;
        }
        Boolean updateLeadStatus = updateLeadService.updateLead(lenderAssociationDetailsDto);
        if (updateLeadStatus) {
            invokeRiskDecision(lenderAssociationDetailsDto);
            log.info("update lead success for {}", applicationId);
        }
        MDC.clear();
    }

    private void createRecord(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto, Long applicationId) {
        lenderAssociationDetailsDto.setApplicationId(applicationId);
        Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(applicationId);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(applicationId, Status.ACTIVE.name(), Lender.PIRAMAL.name());
        if (!lendingApplicationOptional.isPresent() || ObjectUtils.isEmpty(lendingApplicationLenderDetails) || Objects.isNull(lendingApplicationLenderDetails.getLeadId())) {
            log.info("No data found with application id / lead id not saved in lending application lender details for application id: {}", applicationId);
            return;
        }
        LendingApplication lendingApplication = lendingApplicationOptional.get();
        lenderAssociationDetailsDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsDto.setMerchantId(lendingApplication.getMerchantId());
        lenderAssociationDetailsDto.setManageState(Boolean.TRUE);
        lenderAssociationDetailsDto.setModifyLender(enableLenderChange);
    }

    @Transactional
    private void invokeRiskDecision(LenderAssociationDetailsRequestDto lenderAssociationDetailDto) {
        if (riskDecisionSyncService.invokeRiskDecision(lenderAssociationDetailDto)) {
            String currStage =  lenderAssociationDetailDto.getLendingApplicationLenderDetails().getStage();
            LenderAssociationStages nextStage =
                    LenderAssociationStageFactory.getNextStage(Lender.valueOf(lenderAssociationDetailDto.getLendingApplication().getLender()),
                            LenderAssociationStages.valueOf(lenderAssociationDetailDto.getLendingApplicationLenderDetails().getStage()));
            lenderAssociationDetailDto.getLendingApplicationLenderDetails().setStage(nextStage.name());
            commonService.manageApplicationState(lenderAssociationDetailDto);
            nbfcUtils.pushApplicationToNextStage(lenderAssociationDetailDto.getApplicationId(),
                    lenderAssociationDetailDto.getLendingApplication().getLender(),
                    currStage,
                    LenderAssociationStageFactory.autoInvokeNextStage(Lender.valueOf(lenderAssociationDetailDto.getLendingApplication().getLender()), LenderAssociationStages.valueOf(currStage)));
        }

    }
}
