package com.bharatpe.lending.lendingplatform.nbfc.consumer.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LeadSubStatus;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.lendingplatform.nbfc.dto.callback.BRECallback;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LenderApiResponse;
import com.bharatpe.lending.lendingplatform.nbfc.enums.BRERiskDecision;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.bharatpe.lending.lendingplatform.nbfc.enums.WorkflowStage;
import com.bharatpe.lending.lendingplatform.nbfc.registry.WorkflowRegistry;
import com.bharatpe.lending.lendingplatform.nbfc.registry.WorkflowRegistryFactory;
import com.bharatpe.lending.lendingplatform.nbfc.service.workflow.Workflow;
import com.bharatpe.lending.lendingplatform.nbfc.service.workflow.WorkflowManager;
import com.bharatpe.lending.lendingplatform.nbfc.util.WorkflowUtil;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.bharatpe.lending.common.enums.LenderAssociationStages.ASSC_COMPLETED;
import static com.bharatpe.lending.common.enums.LenderAssociationStatus.RISK_FAILED;

@Service
@RequiredArgsConstructor
@Slf4j
public class BRECallbackProcessingService {
    private final LendingApplicationLenderDetailsDao laldDao;
    private final NbfcUtils nbfcUtils;
    private final WorkflowUtil workflowUtil;
    private final ObjectMapper objectMapper;
    private final LendingApplicationDetailsDao lendingApplicationDetailsDao;
    private final WorkflowRegistryFactory workflowRegistryFactory;


    public void processBRECallback(String message) {
        LenderApiResponse<BRECallback> breCallbackResponse = null;
        LendingApplication lendingApplication = null;
        LendingApplicationLenderDetails lald = null;
        try {
            breCallbackResponse = objectMapper.readValue(message, new TypeReference<LenderApiResponse<BRECallback>>() {
            });

            log.info("Processing BRE callback for applicationId: {}", breCallbackResponse.getApplicationId());

            lendingApplication = workflowUtil.getLendingApplication(breCallbackResponse.getApplicationId());
            log.debug("Fetched LendingApplication: {}", lendingApplication);

            lald = workflowUtil.getLendingApplicationLenderDetails(
                    String.valueOf(lendingApplication.getId()), String.valueOf(breCallbackResponse.getLender()));
            log.debug("Fetched LendingApplicationLenderDetails: {}", lald);

            boolean isApproved = isApplicationApproved(breCallbackResponse);
            log.info("BRE decision for applicationId {}: {}", breCallbackResponse.getApplicationId(), isApproved ? "APPROVED" : "FAILED");

            if (isApproved) {
                updateLaldForSuccessCallback(breCallbackResponse, lald);
                if (lendingApplication.getLender() != null && lendingApplication.getLender().equals("CREDITSAISON")) {
                    log.info("Proceeding to the next workflow stage for lender CREDITSAISON after successful BRE approval, lald lead status: {}", lald.getLeadStatus());
                    WorkflowStage nextStage = WorkflowManager.getNextWorkflowStage(lendingApplication.getLender(), lald.getLeadStatus());
                    WorkflowRegistry workflowRegistry = workflowRegistryFactory
                            .getWorkflowRegistry(Lender.valueOf(lendingApplication.getLender()));
                    List<Workflow> workflows = workflowRegistry.getStageWorkflow(nextStage);
                    WorkflowUtil.invokeWorkflows(workflows, lendingApplication.getId());
                } else {
                    updateLad(lendingApplication.getId(), lendingApplication.getLender());
                }
            } else {
                updateLaldForFailedCallback(breCallbackResponse, lald, lendingApplication);
            }
            log.info("BRE processing completed successfully for applicationId: {}", breCallbackResponse.getApplicationId());
        } catch (Exception e) {
            log.error("Exception in processing BRE callback: {}", e.getMessage(), e);
            log.info("modifying lender !");
            nbfcUtils.modifyLender(lendingApplication, lald, RISK_FAILED);
        }
    }

    private void updateLad(Long id, String lender) {
        LendingApplicationDetails lendingApplicationDetails = workflowUtil.getLendingApplicationDetails(String.valueOf(id));
        lendingApplicationDetails.setStage(getStageByLender(lender));
        lendingApplicationDetailsDao.save(lendingApplicationDetails);
    }

    private void updateLaldForFailedCallback(LenderApiResponse<BRECallback> breCallbackResponse, LendingApplicationLenderDetails lald, LendingApplication lendingApplication) {
        log.warn("BRE Failed for applicationId: {}, modifying lender", breCallbackResponse.getApplicationId());
        lald.setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
        lald.setLeadSubStatus(LeadSubStatus.FAILED);
        laldDao.save(lald);
        nbfcUtils.modifyLender(lendingApplication, lald, RISK_FAILED);
    }

    private void updateLaldForSuccessCallback(LenderApiResponse<BRECallback> breCallbackResponse, LendingApplicationLenderDetails lald) {
        log.info("BRE Approved for applicationId: {}, updating statuses", breCallbackResponse.getApplicationId());
        lald.setBreStatus(LenderAssociationStatus.RISK_COMPLETED.name());
        lald.setLeadSubStatus(LeadSubStatus.SUCCESS);
        lald.setStage(getStageByLender(lald.getLender()));
        Optional.ofNullable(breCallbackResponse.getData().getLeadId())
                .ifPresent(lald::setLeadId);
        laldDao.save(lald);
    }

    private String getStageByLender(String lender) {
        if(Lender.CREDITSAISON.name().equalsIgnoreCase(lender)) {
            return LenderAssociationStages.KYC.name();
        }

        return ASSC_COMPLETED.name();
    }

    private boolean isApplicationApproved(LenderApiResponse<BRECallback> breCallbackResponse) {
        boolean approved = Objects.nonNull(breCallbackResponse) &&
                breCallbackResponse.isSuccess() &&
                Objects.nonNull(breCallbackResponse.getData()) &&
                BRERiskDecision.APPROVED.equals(breCallbackResponse.getData().getRiskDecision());
        log.debug("Application approval status for applicationId {}: {}", breCallbackResponse.getApplicationId(), approved);
        return approved;
    }
}