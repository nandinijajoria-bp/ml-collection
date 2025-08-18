package com.bharatpe.lending.lendingplatform.nbfc.consumer.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationVkycDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingApplicationVkycDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.VkycStatus;
import com.bharatpe.lending.lendingplatform.lending.service.VkycServiceV2;
import com.bharatpe.lending.lendingplatform.nbfc.dto.callback.VKYCCallback;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LenderApiResponse;
import com.bharatpe.lending.lendingplatform.nbfc.util.WorkflowUtil;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

@Slf4j
@RequiredArgsConstructor
@Service
public class VKYCCallbackProcessingService {

    private final LendingApplicationVkycDetailsDao laVkycDetailsDao;
    private final LendingApplicationDetailsDao lendingApplicationDetailsDao;
    private final WorkflowUtil workflowUtil;
    private final ObjectMapper objectMapper;
    private final LoanDetailsV3Service loanDetailsV3Service;
    private final VkycServiceV2 vkycServiceV2;

    public void processVKYCCallback(String message) {
        try {
            LenderApiResponse<VKYCCallback> vkycCallbackResponse = objectMapper.readValue(message,
                    new TypeReference<LenderApiResponse<VKYCCallback>>() {
                    });

            if (ObjectUtils.isEmpty(vkycCallbackResponse) || ObjectUtils.isEmpty(vkycCallbackResponse.getData())) {
                log.warn("Invalid VKYC callback request or failed to parse. Message: {}", message);
                return;
            }

            String statusCode = vkycCallbackResponse.getData().getStatus();
            if (ObjectUtils.isEmpty(statusCode)) {

                log.warn("VKYC callback data status is empty for applicationId: {}", vkycCallbackResponse.getApplicationId());
                log.warn("Skipping processing for applicationId due to empty data status: {}", vkycCallbackResponse.getApplicationId());
                return;
            }

            LendingApplication lendingApplication = workflowUtil.getLendingApplication(vkycCallbackResponse.getApplicationId());

            LendingApplicationLenderDetails lald = workflowUtil.getLendingApplicationLenderDetails(
                    String.valueOf(lendingApplication.getId()), vkycCallbackResponse.getLender().toString());

            LendingApplicationVkycDetails vkycDetails = laVkycDetailsDao.findByApplicationIdAndLender(lendingApplication.getId(), lendingApplication.getLender()).orElse(null);
            if (ObjectUtils.isEmpty(vkycDetails)) {
                log.warn("LendingApplicationVkycDetails is null for applicationId: {} and lender: {}", lendingApplication.getId(), lendingApplication.getLender());

                log.warn("Skipping processing for applicationId {} due to missing VKYC details", lendingApplication.getId());
                return;
            }

            // Check if VKYC is in progress | If not then skip processing and return
            if (!VkycStatus.VKYC_IN_PROGRESS.equals(vkycDetails.getStatus())) {
                log.warn("VKYC status is not in progress for applicationId {}: current status is {}", lendingApplication.getId(), vkycDetails.getStatus());
                return;
            }

            // Always process the callback status, regardless of current VKYC status
            vkycServiceV2.handleVKYCStatusUpdate(vkycCallbackResponse.getData(), lendingApplication, vkycDetails, lald);

            log.info("VKYC Callback received for applicationId {}: leadId={}, lender={}, statusCode={}, trackingId={}",
                    vkycCallbackResponse.getApplicationId(), lald.getLeadId(), vkycCallbackResponse.getLender(),
                    statusCode, vkycCallbackResponse.getData().getTrackingId());

            if (LenderAssociationStatus.DRAWDOWN_COMPLETED.name().equals(lald.getDrawDownStatus())) {
                log.info("Drawdown already completed for applicationId: {}. Skipping further processing.", vkycCallbackResponse.getApplicationId());
                return;
            }

            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao
                    .findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
            if (ObjectUtils.isEmpty(lendingApplicationDetails)) {
                log.info("LendingApplicationDetails not found for applicationId: {}", lendingApplication.getId());
                return;
            }

            // Use statusCode from callback to update lender details
            if (VkycStatus.VKYC_COMPLETED.name().equalsIgnoreCase(statusCode)) {
                log.info("VKYC completed successfully for applicationId: {}", lendingApplication.getId());
                vkycServiceV2.updateVKYCStatusToSuccess(lald, lendingApplication);
            } else if (VkycStatus.VKYC_REJECTED.name().equalsIgnoreCase(statusCode)) {
                log.info("VKYC rejected for applicationId: {}", lendingApplication.getId());
                vkycServiceV2.updateVKYCStatusToFailure(lald, lendingApplication);
            } else if (VkycStatus.VKYC_RETRY.name().equalsIgnoreCase(statusCode)) {
                log.info("VKYC retry requested for applicationId: {}", lendingApplication.getId());
                vkycServiceV2.updateVKYCStatusForRetry(lald, lendingApplication, vkycDetails);
            } else if (VkycStatus.VKYC_INITIATED.name().equalsIgnoreCase(statusCode)) {
                log.info("VKYC in_review from auditor for applicationId: {}", lendingApplication.getId());
                vkycServiceV2.updateVKYCPendingAuditor(lald, lendingApplication, vkycDetails);
            } else if (VkycStatus.VKYC_SKIPPED.name().equalsIgnoreCase(statusCode)) {
                log.info("VKYC skipped for applicationId: {}", lendingApplication.getId());
            } else {
                log.warn("Unknown VKYC status code '{}' for applicationId: {}", statusCode, lendingApplication.getId());
            }


            if (VkycStatus.getTerminatedVkycStatusList().contains(vkycDetails.getStatus())) {
                log.info("VKYC terminated for applicationId: {}. Updating application view state.", lendingApplication.getId());
                loanDetailsV3Service.saveApplicationViewState(null, lendingApplication.getId(), LendingViewStates.APPLICATION_STATUS_PAGE);
            }
        } catch (Exception e) {
            log.error("Exception in processing VKYC callback: {}", e.getMessage(), e);
        }
    }
}