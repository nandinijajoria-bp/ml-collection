package com.bharatpe.lending.lendingplatform.nbfc.consumer.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LeadSubStatus;
import com.bharatpe.lending.lendingplatform.nbfc.dto.callback.KYCCallback;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LenderApiResponse;
import com.bharatpe.lending.lendingplatform.nbfc.enums.KycCallbackStatus;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Objects;

import static com.bharatpe.lending.common.enums.LenderAssociationStatus.KYC_FAILED;

@Service
@RequiredArgsConstructor
@Slf4j
public class KYCCallbackProcessingService {

    private final LendingApplicationLenderDetailsDao laldDao;
    private final WorkflowRegistryFactory workflowRegistryFactory;
    private final NbfcUtils nbfcUtils;
    private final WorkflowUtil workflowUtil;
    private final ObjectMapper objectMapper;

    @Transactional
    public void processKYCCallback(String message) {
        LenderApiResponse<KYCCallback> kycCallbackResponse = null;
        LendingApplication lendingApplication = null;
        LendingApplicationLenderDetails lald = null;
        try {
            kycCallbackResponse = objectMapper.readValue(message, new TypeReference<LenderApiResponse<KYCCallback>>() {
            });
            log.info("Processing KYC callback for applicationId: {}", kycCallbackResponse.getApplicationId());

            if (ObjectUtils.isEmpty(kycCallbackResponse) || ObjectUtils.isEmpty(kycCallbackResponse.getData())) {
                log.warn("Invalid or empty KYC callback received");
                return;
            }

            log.debug("Fetching LendingApplication for applicationId: {}", kycCallbackResponse.getApplicationId());
            lendingApplication = workflowUtil.getLendingApplication(kycCallbackResponse.getApplicationId());

            log.debug("Fetching LendingApplicationLenderDetails for applicationId: {} and lender: {}",
                    lendingApplication.getId(), kycCallbackResponse.getLender());
            lald = workflowUtil.getLendingApplicationLenderDetails(
                    String.valueOf(lendingApplication.getId()), kycCallbackResponse.getLender().toString());

            boolean isKYCSuccess = isKYCSuccessful(kycCallbackResponse);
            log.info("KYC Callback Status: {} for applicationId: {}",
                    kycCallbackResponse.getData().getStatus(), kycCallbackResponse.getApplicationId());

            if (isKYCSuccess) {
                log.info("KYC success for applicationId: {}, proceeding to next workflow stage", kycCallbackResponse.getApplicationId());
                lald.setKycStatus(KycCallbackStatus.SUCCESS.name());
                laldDao.save(lald);
                log.debug("Fetching next workflow stage for lender: {} and leadStatus: {}",
                        lendingApplication.getLender(), lald.getLeadStatus());
                WorkflowStage nextStage = WorkflowManager.getNextWorkflowStage(lendingApplication.getLender(), lald.getLeadStatus());
                lald.setStage(!ObjectUtils.isEmpty(nextStage) ? nextStage.name() : "");
                laldDao.save(lald);

                log.debug("Fetching WorkflowRegistry for lender: {}", kycCallbackResponse.getLender());
                WorkflowRegistry workflowRegistry = workflowRegistryFactory.getWorkflowRegistry(kycCallbackResponse.getLender());

                log.info("Triggering workflow actions for stage: {}", nextStage);
                List<Workflow> workflows = workflowRegistry.getStageWorkflow(nextStage);
                WorkflowUtil.invokeWorkflows(workflows, lendingApplication.getId());
            } else {
                log.warn("KYC failed for applicationId: {}, modifying lender", kycCallbackResponse.getApplicationId());
                lald.setKycStatus(KYC_FAILED.name());
                lald.setLeadSubStatus(LeadSubStatus.FAILED);
                laldDao.save(lald);
                nbfcUtils.modifyLender(lendingApplication, lald, KYC_FAILED);
                laldDao.save(lald);
            }
        } catch (Exception e) {
            log.error("Exception in processing KYC callback: {}", e.getMessage(), e);
            log.info("modifying lender !");
            nbfcUtils.modifyLender(lendingApplication, lald, KYC_FAILED);
        }
    }

    private boolean isKYCSuccessful(LenderApiResponse<KYCCallback> kycCallbackResponse) {
        return Objects.nonNull(kycCallbackResponse.getData()) &&
                kycCallbackResponse.isSuccess() &&
                KycCallbackStatus.SUCCESS.equals(kycCallbackResponse.getData().getStatus());
    }
}