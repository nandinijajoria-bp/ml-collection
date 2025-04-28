package com.bharatpe.lending.lendingplatform.nbfc.consumer.service;

import com.bharatpe.lending.common.dto.projection.LALDWorkflowDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.RetryWorkflowMessage;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.bharatpe.lending.lendingplatform.nbfc.enums.WorkflowStage;
import com.bharatpe.lending.lendingplatform.nbfc.registry.WorkflowRegistryFactory;
import com.bharatpe.lending.lendingplatform.nbfc.service.LenderWorkflowRetryDetailService;
import com.bharatpe.lending.lendingplatform.nbfc.service.database.LendingApplicationLenderDetailsService;
import com.bharatpe.lending.lendingplatform.nbfc.service.workflow.Workflow;
import com.bharatpe.lending.lendingplatform.nbfc.service.workflow.WorkflowManager;
import com.bharatpe.lending.lendingplatform.nbfc.util.WorkflowUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.List;

import static com.bharatpe.lending.common.enums.lendingplatform.LenderWorkflowRetryDetailStatus.ACKNOWLEDGED;

@Service
@Slf4j
@RequiredArgsConstructor
public class RetryLenderWorkflowProcessingService {
    private final LenderWorkflowRetryDetailService lenderWorkflowRetryDetailService;
    private final LendingApplicationLenderDetailsService lendingApplicationLenderDetailsService;
    private final WorkflowRegistryFactory workflowRegistryFactory;

    public void processRetryWorkflowMessage(RetryWorkflowMessage retryWorkflowMessage) {
        log.info("Processing retry workflow message : {}", retryWorkflowMessage);
        lenderWorkflowRetryDetailService.updateStatus(retryWorkflowMessage.getLenderWorkflowRetryId(), ACKNOWLEDGED);
        LALDWorkflowDetails laldWorkflowDetails = lendingApplicationLenderDetailsService
                .getLaldWorkflowDetailsByApplicationId(retryWorkflowMessage.getApplicationId());
        if (ObjectUtils.isEmpty(laldWorkflowDetails) || ObjectUtils.isEmpty(laldWorkflowDetails.getLeadStatus())
                || ObjectUtils.isEmpty(laldWorkflowDetails.getLeadSubStatus())) {
            log.error("LALD details not found for applicationId: {}", retryWorkflowMessage.getApplicationId());
            return;
        }
        log.info("LALD lead status {}, lead sub status: {} for applicationId: {}", laldWorkflowDetails.getLeadStatus(),
                laldWorkflowDetails.getLeadSubStatus(), retryWorkflowMessage.getApplicationId());
        initiateWorkflow(laldWorkflowDetails);
    }

    private void initiateWorkflow(LALDWorkflowDetails laldWorkflowDetails) {
        WorkflowStage workflowStage = WorkflowManager.getCurrentWorkflowStage(laldWorkflowDetails.getLender(),
                laldWorkflowDetails.getLeadStatus());
        if (ObjectUtils.isEmpty(workflowStage)) {
            log.error("Workflow not initiated for application id : {} as workflow stage is empty", laldWorkflowDetails.getApplicationId());
            return;
        }
        log.info("Workflow stage for applicationId : {} lead status: {} is : {}", laldWorkflowDetails.getApplicationId(),
                laldWorkflowDetails.getLeadStatus(), workflowStage);
        List<Workflow> workflows = workflowRegistryFactory.getWorkflowRegistry(Lender.valueOf(laldWorkflowDetails.getLender()))
                .getStageWorkflow(workflowStage);
        WorkflowUtil.invokeWorkflows(workflows, laldWorkflowDetails.getApplicationId());
    }
}
