package com.bharatpe.lending.lendingplatform.nbfc.consumer.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationKycDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationKycDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.lendingplatform.nbfc.dto.callback.KYCCallback;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LenderApiResponse;
import com.bharatpe.lending.lendingplatform.nbfc.enums.KycCallbackStatus;
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
import org.springframework.util.ObjectUtils;

import java.util.Date;
import java.util.List;

import static com.bharatpe.lending.common.enums.LenderAssociationStatus.EKYC_FAILED;
import static com.bharatpe.lending.loanV3.utils.KycUtils.getFatherName;

@Slf4j
@RequiredArgsConstructor
@Service
public class EKycCallbackProcessingService {

    private final LendingApplicationKycDetailsDao lendingApplicationKycDetailsDao;
    private final WorkflowRegistryFactory workflowRegistryFactory;
    private final NbfcUtils nbfcUtils;
    private final WorkflowUtil workflowUtil;
    private final LendingApplicationLenderDetailsDao laldDao;
    private final ObjectMapper objectMapper;

    public void processEKYCCallback(String message) {
        LenderApiResponse<KYCCallback> ekycCallbackResponse = null;
        LendingApplication lendingApplication = null;
        LendingApplicationLenderDetails lald = null;
        try {
            ekycCallbackResponse = objectMapper.readValue(message, new TypeReference<LenderApiResponse<KYCCallback>>() {
            });

            if (ObjectUtils.isEmpty(ekycCallbackResponse) || ObjectUtils.isEmpty(ekycCallbackResponse.getApplicationId())) {
                log.warn("Invalid eKYC callback response received.");
                return;
            }

            log.info("Processing eKYC callback for applicationId: {}", ekycCallbackResponse.getApplicationId());

            lendingApplication = workflowUtil.getLendingApplication(ekycCallbackResponse.getApplicationId());

            log.debug("Fetching LendingApplicationLenderDetails for applicationId: {} and lender: {}",
                    lendingApplication.getId(), ekycCallbackResponse.getLender());

            lald = workflowUtil.getLendingApplicationLenderDetails(
                    String.valueOf(lendingApplication.getId()),
                    ekycCallbackResponse.getLender().name()
            );

            log.info("Current eKYC status for applicationId {}: {}", lendingApplication.getId(), lald.getKycStatus());

            if (!(LenderAssociationStatus.EKYC_INITIATED.name().equals(lald.getStatus()) ||
                    LenderAssociationStatus.EKYC_IN_PROGRESS.name().equals(lald.getStatus()))) {
                log.warn("Invalid eKYC status {} for applicationId {}", lald.getKycStatus(), lendingApplication.getId());
                return;
            }

            KYCCallback kycData = ekycCallbackResponse.getData();
            if (ObjectUtils.isEmpty(kycData)) {
                log.warn("Received empty KYC data in callback for applicationId: {}", lendingApplication.getId());
                return;
            }

            log.info("eKYC Callback Status: {} for applicationId: {}", kycData.getStatus(), ekycCallbackResponse.getApplicationId());

            if (ekycCallbackResponse.isSuccess() && KycCallbackStatus.SUCCESS.equals(kycData.getStatus())) {
                log.info("eKYC success for applicationId: {}, updating KYC details.", ekycCallbackResponse.getApplicationId());
                updateKycDetails(lendingApplication.getId(), kycData);
                lald.setKycStatus(KycCallbackStatus.SUCCESS.name());
                laldDao.save(lald);
                triggerWorkflow(lald, lendingApplication);
            } else {
                log.warn("eKYC failed for applicationId: {}, modifying lender.", ekycCallbackResponse.getApplicationId());
                lald.setKycStatus(EKYC_FAILED.name());
                laldDao.save(lald);
                nbfcUtils.modifyLender(lendingApplication, lald, EKYC_FAILED);
            }
        } catch (Exception e) {
            log.error("Exception in processing EKYC callback: {}", e.getMessage(), e);
            log.warn("modifying lender !");
            nbfcUtils.modifyLender(lendingApplication, lald, EKYC_FAILED);
        }

    }

    private void updateKycDetails(Long applicationId, KYCCallback kycData) {
        log.debug("Fetching existing KYC details for applicationId: {}", applicationId);

        LendingApplicationKycDetails kycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdOrderByIdDesc(applicationId);
        if (ObjectUtils.isEmpty(kycDetails)) {
            log.warn("No existing KYC details found for applicationId: {}", applicationId);
            return;
        }

        log.info("Updating KYC details for applicationId: {}", applicationId);

        if (!ObjectUtils.isEmpty(kycData.getAadhaarDetails())) {
            kycDetails.setAadharAddress(kycData.getAadhaarDetails().getAddress());
            kycDetails.setAadharApprovedAt(new Date());
            kycDetails.setAadharName(kycData.getAadhaarDetails().getName());
            kycDetails.setFatherName(getFatherName(kycData.getAadhaarDetails().getCareOf() + ","));
            kycDetails.setDob(String.valueOf(kycData.getAadhaarDetails().getDob()));
            kycDetails.setAadharIdentifier(kycData.getAadhaarDetails().getAadhaarNumber());
//            kycDetails.setAadharXml(kycData.getAadhaarDetails().getAadhaarXML());
            kycDetails.setGender(kycData.getAadhaarDetails().getGender());
            kycDetails.setAadharState(kycData.getAadhaarDetails().getState());
            kycDetails.setAadharCity(kycData.getAadhaarDetails().getCity());
            kycDetails.setAadharPinCode(String.valueOf(kycData.getAadhaarDetails().getPincode()));
            lendingApplicationKycDetailsDao.save(kycDetails);
        } else {
            log.warn("Aadhaar details missing in KYC callback for applicationId: {}", applicationId);
        }
    }

    private void triggerWorkflow(LendingApplicationLenderDetails lald, LendingApplication lendingApplication) {
        log.info("Fetching next workflow stage for applicationId: {}", lendingApplication.getId());

        WorkflowStage nextStage = WorkflowManager.getNextWorkflowStage(lendingApplication.getLender(), lald.getLeadStatus());
        WorkflowRegistry workflowRegistry = workflowRegistryFactory.getWorkflowRegistry(Lender.valueOf(lendingApplication.getLender()));

        log.info("Triggering workflow actions for stage: {} and applicationId: {}", nextStage, lendingApplication.getId());

        List<Workflow> workflows = workflowRegistry.getStageWorkflow(nextStage);
        WorkflowUtil.invokeWorkflows(workflows, lendingApplication.getId());

        log.info("eKYC workflow executed successfully for applicationId: {}", lendingApplication.getId());
    }
}