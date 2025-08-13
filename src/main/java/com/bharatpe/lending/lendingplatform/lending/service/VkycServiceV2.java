package com.bharatpe.lending.lendingplatform.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationKycDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationVkycDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationKycDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingApplicationVkycDetails;
import com.bharatpe.lending.common.enums.LeadSubStatus;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.VkycStatus;
import com.bharatpe.lending.config.VkycConfig;
import com.bharatpe.lending.dto.vkyc.response.VKycInitiateResponseDto;
import com.bharatpe.lending.lendingplatform.lending.util.RolloutUtil;
import com.bharatpe.lending.lendingplatform.nbfc.client.LendingPlatformClient;
import com.bharatpe.lending.lendingplatform.nbfc.dto.callback.VKYCCallback;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.LenderBaseRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.VKYCStatusCheckRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LenderApiResponse;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.request.CsVkycStatusCheckRequestBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.workflow.VKYCWorkflow;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Date;

import static com.bharatpe.lending.common.enums.VkycStatus.VKYC_REJECTED;
import static com.bharatpe.lending.common.enums.VkycStatus.VKYC_SKIPPED;
import static com.bharatpe.lending.dto.vkyc.response.VkycStatusResponseDto.Status.MANUALLY_APPROVED;
import static com.bharatpe.lending.dto.vkyc.response.VkycStatusResponseDto.Status.NEEDS_REVIEW;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.LeadStatus.VKYC;

/**
 * Service for handling vKYC operations for the lending platform.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VkycServiceV2 {

    private final LendingApplicationVkycDetailsDao lendingApplicationVkycDetailsDao;
    private final LendingApplicationKycDetailsDao lendingApplicationKycDetailsDao;
    private final LendingApplicationLenderDetailsDao laldDao;
    private final ObjectMapper objectMapper;
    private final VkycConfig vkycConfig;
    private final VKYCWorkflow vKycWorkflow;
    private final CsVkycStatusCheckRequestBuilder statusCheckRequestBuilder;
    private final LendingPlatformClient lendingPlatformClient;
    private final CommonService commonService;
    private final RolloutUtil rolloutUtil;

    private static final String LENDER_DETAILS = "LenderDetails";

    public ApiResponse<?> processVKycInitiation(LendingApplication lendingApplication, LendingApplicationLenderDetails lald,
                                                LendingApplicationVkycDetails vkycDetails, Boolean isRetry) {

        if (isDisableInitiateVkycSession(vkycDetails)) {
            log.info("Initiate vkyc session is disabled for given lender {} and applicationId {}", lendingApplication.getLender(), lald.getApplicationId());
            return new ApiResponse<>(false, null, "Initiate vkyc session is skipped for this application");
        }

        ApiResponse<?> responseDTO = initiateVKyc(lendingApplication, lald, vkycDetails, isRetry);
        if (ObjectUtils.isEmpty(responseDTO) || ObjectUtils.isEmpty(responseDTO.getData())) {
            log.error("Failed to initiate vKYC for applicationId: {}, lender: {}, response: {}",
                    lendingApplication.getId(), lendingApplication.getLender(), responseDTO);
            // If the response is empty or data is not present, we return an error response
            return new ApiResponse<>(false, null, "An unexpected error occurred while initiating vKYC. Please try again later.");
        }
        VKycInitiateResponseDto initiateResponse = objectMapper.convertValue(responseDTO.getData(), VKycInitiateResponseDto.class);
        log.info("vKyc initiate response of {} from nbfc with applicationId: {}, {}", lendingApplication.getLender(), lendingApplication.getId(), initiateResponse);
        if (ObjectUtils.isEmpty(initiateResponse) || ObjectUtils.isEmpty(initiateResponse.getSessionUrl())) {
            log.error("vKYC initiation failed for applicationId: {}, lender: {}, response: {}",
                    lendingApplication.getId(), lendingApplication.getLender(), initiateResponse);
            // If the response is empty or session URL is not present, we return an error response
            return new ApiResponse<>(false, null, "Unable to generate vKYC session link at this time. Please try again later.");
        }
        vkycDetails.setSessionUrl(initiateResponse.getSessionUrl());
        vkycDetails.setStatus(VkycStatus.VKYC_IN_PROGRESS);
        saveAndLogVkycDetails(vkycDetails, lendingApplication, "VKYC details updated after initiation");
        return new ApiResponse<>(initiateResponse);
    }

    /**
     * Initiates vKYC for the given application and lender details.
     *
     * @param lendingApplication the lending application
     * @param lald                 the lender details
     * @param vkycDetails        the vKYC details
     * @param isRetry            whether this is a retry attempt
     * @return ApiResponse with vKYC initiation result
     */
    private ApiResponse<?> initiateVKyc(LendingApplication lendingApplication, LendingApplicationLenderDetails lald, LendingApplicationVkycDetails vkycDetails, Boolean isRetry) {
        try {
            log.info("Initiating vKYC for applicationId: {}, lender: {}, leadId: {}, vkycStatus: {}, vkycEligible: {}, isRetry: {}",
                    lendingApplication.getId(), lendingApplication.getLender(), lald != null ? lald.getLeadId() : null,
                    vkycDetails != null ? vkycDetails.getStatus() : null, vkycDetails != null ? vkycDetails.getVkycEligible() : null, isRetry);

            // This all are pre-checks to ensure that we have all the required details before initiating vKYC
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("No application found for given applicationId {}", lald != null ? lald.getApplicationId() : null);
                return new ApiResponse<>(false, null, "No Application found for applicationId");
            }
            if (ObjectUtils.isEmpty(lald) || ObjectUtils.isEmpty(lald.getLeadId())) {
                log.info("No lender details or lead id found for given lender {} and applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
                return new ApiResponse<>(false, null, "No Lender details found for given applicationId and lender");
            }
            if (ObjectUtils.isEmpty(vkycDetails) || !Boolean.TRUE.equals(vkycDetails.getVkycEligible())) {
                log.info("No vkyc details found or vkyc not eligible for given lender {} and applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
                return new ApiResponse<>(false, null, "No vkyc details found or vkyc not eligible for given lender");
            }

            log.info("VKYC Approved checks for application: {}, lender: {}, leadId: {}, vkycStatus: {}, vkycEligible: {}, isRetry: {}",
                    lendingApplication.getId(), lendingApplication.getLender(), lald.getLeadId(), vkycDetails.getStatus(), vkycDetails.getVkycEligible(), isRetry);
            VKycInitiateResponseDto initiateResponse = vKycWorkflow.invoke(String.valueOf(lendingApplication.getId()));
            if (ObjectUtils.isEmpty(initiateResponse) || ObjectUtils.isEmpty(initiateResponse.getSessionUrl())) {
                log.info("VKYC initiation failed for applicationId: {}, lender: {}, response: {}",
                        lendingApplication.getId(), lendingApplication.getLender(), initiateResponse);
                return new ApiResponse<>(false, null, "Could not generate vkyc session link, please try again later");
            }
            log.info("VKYC initiation for applicationId: {}, response: {}", lendingApplication.getId(), initiateResponse);
            return new ApiResponse<>(initiateResponse);
        } catch (Exception e) {
            log.error("Exception in initiating vKyc of {} for applicationId {}: {}",
                    lendingApplication != null ? lendingApplication.getLender() : null,
                    lendingApplication != null ? lendingApplication.getId() : null,
                    e.getMessage(), e);
        }
        return new ApiResponse<>(false, null, "Something went wrong while initiating vKyc");
    }

    // This method updates the vKYC details and return for V3 API
    public void updateVkycDetailsForCreditSaison(LendingApplication lendingApplication,
                                                 LendingApplicationVkycDetails vkycDetails,
                                                 LendingApplicationLenderDetails lald,
                                                 Integer appVersion) {
        // Credit Saison does not use dKYC
        vkycDetails.setDkycEligible(false);

        // If vKYC is already in progress, check and update status, then exit
        if (VkycStatus.VKYC_IN_PROGRESS.equals(vkycDetails.getStatus())) {
            vkycStatusCheck(lendingApplication, vkycDetails, lald);
            log.info("vKYC is already in progress for applicationId: {}, lender: {}, leadId: {}",
                    lendingApplication.getId(), lendingApplication.getLender(), lald.getLeadId());
            return;
        }

        // Set vKYC as eligible by default
        vkycDetails.setVkycEligible(true);

        // Disable vKYC session if required
        if (isDisableInitiateVkycSession(vkycDetails, appVersion)) {
            log.info("vKYC session is disabled for applicationId: {}, lender: {}, leadId: {}",
                    lendingApplication.getId(), lendingApplication.getLender(), lald.getLeadId());
            vkycDetails.setVkycEligible(false);
            setVkycSkipped(vkycDetails, lald, lendingApplication, vkycDetails.getSessionStatus(),
                    "VKYC session disabled for Credit Saison");
        }

        // Persist the updated vKYC details
        saveAndLogVkycDetails(vkycDetails, lendingApplication, "VKYC details updated for Credit Saison");
    }

    public void vkycStatusCheck(LendingApplication lendingApplication, LendingApplicationVkycDetails vkycDetails, LendingApplicationLenderDetails lald) {
        log.info("vKYC status check for applicationId: {}, lender: {}, leadId: {}",
                lendingApplication.getId(), lendingApplication.getLender(), lald.getLeadId());
        LenderApiResponse<VKYCCallback> apiResponse = lendingPlatformClient.checkVKYCStatusCheck(getVKYCStatusCheckRequest(lendingApplication));
        log.info("vKYC status check apiResponse for applicationId: {}, lender: {}, leadId: {}, Response: {}",
                lendingApplication.getId(), lendingApplication.getLender(), lald.getLeadId(), apiResponse);
        processVKYCStatusCheckResponse(lendingApplication, vkycDetails, apiResponse, lald);
        log.info("Checking vKYC status for applicationId: {}, lender: {}, leadId: {}",
                lendingApplication.getId(), lendingApplication.getLender(), lald.getLeadId());
    }

    private LenderBaseRequest<VKYCStatusCheckRequest> getVKYCStatusCheckRequest(LendingApplication lendingApplication) {
        try {
            VKYCStatusCheckRequest vkycStatusCheckRequest = statusCheckRequestBuilder.buildRequest(lendingApplication);
            return LenderBaseRequest.<VKYCStatusCheckRequest>builder()
                    .applicationId(String.valueOf(lendingApplication.getId()))
                    .customerId(String.valueOf(lendingApplication.getMerchantId()))
                    .lender(Lender.valueOf(lendingApplication.getLender()))
                    .data(vkycStatusCheckRequest)
                    .build();
        } catch (Exception e) {
            log.error("Error while creating VKYC StatusCheck request for applicationId={}, error: {}",
                    lendingApplication.getId(), e.getMessage(), e);
            return null;
        }
    }


    public boolean isDisableInitiateVkycSession(LendingApplicationVkycDetails vkycDetails, Integer appVersion) {
        if (!ObjectUtils.isEmpty(appVersion) && appVersion < vkycConfig.getMinAppVersionForCs()) {
            log.info("vkyc session is disabled for applicationId {} as app version {} is not supported, min app version req {}", vkycDetails.getApplicationId(), appVersion, vkycConfig.getMinAppVersionForCs());
            vkycDetails.setSessionStatus(VkycStatus.SessionStatus.APP_VERSION_NOT_SUPPORTED.name());
            lendingApplicationVkycDetailsDao.save(vkycDetails);
            return true;
        }

        log.info("Checking if vkyc session is disabled for applicationId {} with session status {}", vkycDetails.getApplicationId(), vkycDetails.getSessionStatus());
        return isDisableInitiateVkycSession(vkycDetails);
    }

    public boolean isDisableInitiateVkycSession(LendingApplicationVkycDetails vkycDetails) {
        if (VkycStatus.getVkycDisabledSessionStatuses().contains(vkycDetails.getSessionStatus())) {
            log.info("vkyc session is {} disabled for applicationId {}", vkycDetails.getSessionStatus(), vkycDetails.getApplicationId());
            return true;
        }

        LendingApplicationKycDetails kycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(
                vkycDetails.getApplicationId(), vkycDetails.getLender());
        if (!ObjectUtils.isEmpty(kycDetails) && !ObjectUtils.isEmpty(kycDetails.getAadharApprovedAt())) {
            Date approvedAt = kycDetails.getAadharApprovedAt();
            Date now = new Date();
            long durationMillis = now.getTime() - approvedAt.getTime();
            log.info("KycDetails Aadhaar approved at {} for applicationId {}, duration since approval is {} ms, now={}",
                    approvedAt, vkycDetails.getApplicationId(), durationMillis, now);
            long expiryMillis = vkycConfig.getAadhaarExpiryTatInHousForCreditSaison() * 60L * 60L * 1000L;
            if (durationMillis > expiryMillis) {
                log.info("vkyc aadhaar tat {} hours is breached: {} ms > {} ms for applicationId {}",
                        vkycConfig.getAadhaarExpiryTatInHousForCreditSaison(), durationMillis, expiryMillis, vkycDetails.getApplicationId());
                vkycDetails.setSessionStatus(VkycStatus.SessionStatus.AADHAAR_XML_EXPIRED.name());
                lendingApplicationVkycDetailsDao.save(vkycDetails);
                return true;
            }
        }
        return false;
    }

    public void processVKYCStatusCheckResponse(LendingApplication lendingApplication, LendingApplicationVkycDetails vkycDetails,
                                               LenderApiResponse<VKYCCallback> vkycStatusCheckResponse, LendingApplicationLenderDetails lald) {
        try {
            log.info("Processing VKYC Status Check response for applicationId: {}, lender: {}, bpLoanId: {}",
                    lendingApplication.getId(), lendingApplication.getLender(), lendingApplication.getExternalLoanId());

            if (ObjectUtils.isEmpty(vkycStatusCheckResponse) || ObjectUtils.isEmpty(vkycStatusCheckResponse.getData())) {
                log.info("VKYC StatusCheck response is empty for applicationId: {}", lendingApplication.getId());
                return;
            }

            if (!vkycStatusCheckResponse.isSuccess()) {
                log.info("VKYC StatusCheck for applicationId: {}, status: {}", lendingApplication.getId(), vkycStatusCheckResponse.getData().getStatus());
                return;
            }

            if (ObjectUtils.isEmpty(vkycStatusCheckResponse.getData().getStatus())) {
                log.info("VKYC StatusCheck response status is empty for applicationId: {}", lendingApplication.getId());
                return;
            }

            String statusCode = vkycStatusCheckResponse.getData().getStatus();

            // Handle VKYC status update based on the callback response from lending Platform
            handleVKYCStatusUpdate(vkycStatusCheckResponse.getData(), lendingApplication, vkycDetails, lald);

            // Log VKYC callback details directly
            log.info("VKYC Callback received for applicationId {}: leadId={}, lender={}, statusCode={}, trackingId={}",
                    vkycStatusCheckResponse.getApplicationId(), lald.getLeadId(), vkycStatusCheckResponse.getLender(),
                    vkycStatusCheckResponse.getData().getStatus(), vkycStatusCheckResponse.getData().getTrackingId());


            if (VkycStatus.VKYC_COMPLETED.name().equalsIgnoreCase(statusCode)) {
                log.info("VKYC completed for applicationId: {}, leadId: {}, lender: {}", lendingApplication.getId(), lald.getLeadId(), lendingApplication.getLender());
                updateVKYCStatusToSuccess(lald, lendingApplication);
            } else if (VkycStatus.VKYC_REJECTED.name().equalsIgnoreCase(statusCode)) {
                log.info("VKYC rejected for applicationId: {}, leadId: {}, lender: {}", lendingApplication.getId(), lald.getLeadId(), lendingApplication.getLender());
                updateVKYCStatusToFailure(lald, lendingApplication);
            } else if (VkycStatus.VKYC_RETRY.name().equalsIgnoreCase(statusCode)) {
                log.info("VKYC retry for applicationId: {}, leadId: {}, lender: {}", lendingApplication.getId(), lald.getLeadId(), lendingApplication.getLender());
                updateVKYCStatusForRetry(lald, lendingApplication, vkycDetails);
            } else if (VkycStatus.VKYC_INITIATED.name().equalsIgnoreCase(statusCode)) {
                log.info("VKYC status in_review from auditor for applicationId: {}, leadId: {}, lender: {}", lendingApplication.getId(), lald.getLeadId(), lendingApplication.getLender());
                updateVKYCPendingAuditor(lald, lendingApplication, vkycDetails);
            } else {
                log.warn("Unknown VKYC status code '{}' for applicationId: {} merchantId:{}", statusCode, lendingApplication.getId(), lendingApplication.getMerchantId());
            }
        } catch (Exception e) {
            log.error("Exception in processing VKYC callback: {}", e.getMessage(), e);
        }

    }

    public void handleVKYCStatusUpdate(VKYCCallback vkycResponse, LendingApplication lendingApplication,
                                       LendingApplicationVkycDetails vkycDetails, LendingApplicationLenderDetails lald) {

        log.info("Updating VKYC status for applicationId {} merchantId:{} with response {}", lendingApplication.getId(), lendingApplication.getMerchantId(), vkycResponse);
        vkycDetails.setStatus(VkycStatus.valueOf(vkycResponse.getStatus()));
        saveAndLogVkycDetails(vkycDetails, lendingApplication, "VKYC status updated from API response");

        if (VkycStatus.VKYC_COMPLETED.equals(vkycDetails.getStatus())) {
            log.info("VKYC completed for applicationId: {}", lendingApplication.getId());
            vkycDetails.setApprovedAt(new Date());
            vkycDetails.setSessionStatus(MANUALLY_APPROVED.name());
        }
        saveAndLogVkycDetails(vkycDetails, lendingApplication, "VKYC status updated");

        if (VKYC_REJECTED.equals(vkycDetails.getStatus())) {
            log.info("VKYC rejected for applicationId: {} merchantId:{}", lendingApplication.getId(), lendingApplication.getMerchantId());
            setVkycRejected(vkycDetails, lald, lendingApplication, "VKYC details updated with rejection");
            commonService.rejectApplication(lendingApplication, lald);
            return;
        }

        if (VkycStatus.VKYC_RETRY.equals(vkycDetails.getStatus())) {
            log.info("VKYC retry for applicationId: {} merchantId:{}", lendingApplication.getId(), lendingApplication.getMerchantId());
            setVkycRetry(vkycDetails, lald, lendingApplication, "LendingApplicationLenderDetails updated with VKYC_RETRY status");
        }
    }

    public void handleVKYCRetryApplications(LendingApplication lendingApplication, LendingApplicationVkycDetails vkycDetails, LendingApplicationLenderDetails lald) {
        vkycDetails.setVkycRetryCount(vkycDetails.getVkycRetryCount() + 1);
        if (vkycDetails.getVkycRetryCount() <= vkycConfig.getMaxRetryCountForCs()) {
            setVkycRetry(vkycDetails, lald, lendingApplication, "VKYC retry count updated");
            log.info("VKYC retry count {} for applicationId {} merchantId {} is within limit, updating status to VKYC_RETRY",
                    vkycDetails.getVkycRetryCount(), lendingApplication.getId(), lendingApplication.getMerchantId());
            return;
        }
        if (vkycConfig.getRetryExhaustFlowEnabledLenders().contains(lendingApplication.getLender())) {
            setVkycSkipped(vkycDetails, lald, lendingApplication, VkycStatus.SessionStatus.VKYC_RETRY_EXHAUST.name(), "VKYC retry exhausted");
            log.info("Skipping VKYC as retry count {} exceeds max retry count {} for applicationId {}", vkycDetails.getVkycRetryCount(), vkycConfig.getMaxRetryCount(), lendingApplication.getId());
        }
    }

    public void updateVKYCStatusToSuccess(LendingApplicationLenderDetails lald, LendingApplication lendingApplication) {
        setVkycSuccess(lald, lendingApplication, "VKYC marked as SUCCESS");
    }

    public void updateVKYCStatusToFailure(LendingApplicationLenderDetails lald, LendingApplication lendingApplication) {
        setVkycFailure(lald, lendingApplication, "VKYC marked as FAILURE");
    }

    public void updateVKYCStatusForRetry(LendingApplicationLenderDetails lald, LendingApplication lendingApplication, LendingApplicationVkycDetails vkycDetails) {
        setVkycRetry(vkycDetails, lald, lendingApplication, "VKYC marked as RETRY");
        handleVKYCRetryApplications(lendingApplication, vkycDetails, lald);
        log.info("VKYC retry status has been processed for applicationId: {} merchantId: {}", lendingApplication.getId(), lendingApplication.getMerchantId());
    }

    public void updateVKYCPendingAuditor(LendingApplicationLenderDetails lald, LendingApplication lendingApplication, LendingApplicationVkycDetails vkycDetails) {
        setVkycInReview(lald, vkycDetails, lendingApplication, "VKYC marked as IN_REVIEW");
        log.info("VKYC marked as IN_REVIEW for auditor review for applicationId: {} merchantId: {}", lendingApplication.getId(), lendingApplication.getMerchantId());
    }

    public Boolean rejectApplicationIfRequired(LendingApplication lendingApplication,
                                               LendingApplicationVkycDetails vkycDetails,
                                               LendingApplicationLenderDetails lald) {
        boolean shouldSkip = false;
        String skipReason = null;

        if (!rolloutUtil.isEligibleForCreditSaisonVkyc(lendingApplication.getMerchantId())) {
            log.error("CS: vKYC eligibility revoked for applicationId={}, merchantId={}. Rejecting vKYC after previous enablement.",
                    lendingApplication.getId(), lendingApplication.getMerchantId());
            shouldSkip = true;
            skipReason = "vKYC eligibility revoked";
        }
        if (isDisableInitiateVkycSession(vkycDetails)) {
            log.info("CS: vKYC session is disabled for applicationId={} merchantId={}, skipping vKYC",
                    lendingApplication.getId(), lendingApplication.getMerchantId());
            shouldSkip = true;
            skipReason = vkycDetails.getSessionStatus();
        }
        if (shouldSkip) {
            setVkycSkipped(vkycDetails, lald, lendingApplication, null != skipReason ? skipReason : VKYC_SKIPPED.name(), "VKYC skipped");
            return true;
        }
        log.info("CS: VKYC Finally No Skip required for applicationId={}, merchantId={}, vKYC status={}, bpLoanId={}",
                lendingApplication.getId(), lendingApplication.getMerchantId(), vkycDetails.getStatus(), lendingApplication.getExternalLoanId());
        return false;
    }

    // --- Helper methods for VKYC status transitions ---
    private void setVkycSkipped(LendingApplicationVkycDetails vkycDetails, LendingApplicationLenderDetails lald, LendingApplication lendingApplication, String sessionStatus, String logMsg) {
        vkycDetails.setSessionStatus(sessionStatus);
        vkycDetails.setStatus(VkycStatus.VKYC_SKIPPED);
        saveAndLogVkycDetails(vkycDetails, lendingApplication, logMsg);

        lald.setLeadStatus(VKYC.name());
        lald.setSanctionStatus(LenderAssociationStages.SKIP_VKYC.name());
        lald.setLeadSubStatus(LeadSubStatus.SKIPPED);
        saveAndLogLenderDetails(lald, lendingApplication, logMsg + LENDER_DETAILS);

    }

    private void setVkycRejected(LendingApplicationVkycDetails vkycDetails, LendingApplicationLenderDetails lald, LendingApplication lendingApplication, String logMsg) {
        vkycDetails.setSessionStatus(VKYC_REJECTED.name());
        vkycDetails.setRejectReason(VKYC_REJECTED.name());
        saveAndLogVkycDetails(vkycDetails, lendingApplication, logMsg);

        lald.setLeadStatus(VKYC.name());
        lald.setLeadSubStatus(LeadSubStatus.REJECTED);
        saveAndLogLenderDetails(lald, lendingApplication, logMsg + LENDER_DETAILS);
    }

    private void setVkycRetry(LendingApplicationVkycDetails vkycDetails, LendingApplicationLenderDetails lald, LendingApplication lendingApplication, String logMsg) {
        vkycDetails.setStatus(VkycStatus.VKYC_RETRY);
        vkycDetails.setSessionStatus(VkycStatus.VKYC_RETRY.name());
        saveAndLogVkycDetails(vkycDetails, lendingApplication, logMsg);

        lald.setLeadStatus(VKYC.name());
        lald.setLeadSubStatus(LeadSubStatus.RETRY);
        saveAndLogLenderDetails(lald, lendingApplication, logMsg + LENDER_DETAILS);
    }

    private void setVkycSuccess(LendingApplicationLenderDetails lald, LendingApplication lendingApplication, String logMsg) {
        lald.setLeadStatus(VKYC.name());
        lald.setLeadSubStatus(LeadSubStatus.SUCCESS);
        saveAndLogLenderDetails(lald, lendingApplication, logMsg);
    }

    private void setVkycFailure(LendingApplicationLenderDetails lald, LendingApplication lendingApplication, String logMsg) {
        lald.setLeadStatus(VKYC.name());
        lald.setLeadSubStatus(LeadSubStatus.REJECTED);
        saveAndLogLenderDetails(lald, lendingApplication, logMsg);
    }

    private void setVkycInReview(LendingApplicationLenderDetails lald, LendingApplicationVkycDetails vkycDetails, LendingApplication lendingApplication, String logMsg) {
        vkycDetails.setSessionStatus(NEEDS_REVIEW.name());
        vkycDetails.setStatus(VkycStatus.VKYC_IN_PROGRESS); // This is because the vKYC is in review by auditor
        saveAndLogVkycDetails(vkycDetails, lendingApplication, logMsg);

        lald.setLeadStatus(VKYC.name());
        lald.setLeadSubStatus(LeadSubStatus.IN_REVIEW);
        saveAndLogLenderDetails(lald, lendingApplication, logMsg + LENDER_DETAILS);
    }

    // Utility: Save VKYC details and log
    private void saveAndLogVkycDetails(LendingApplicationVkycDetails vkycDetails, LendingApplication lendingApplication, String message) {
        lendingApplicationVkycDetailsDao.save(vkycDetails);
        log.info("{} for applicationId: {}, merchantId: {}, details: {}", message, lendingApplication.getId(), lendingApplication.getMerchantId(), vkycDetails);
    }

    // Utility: Save and log lender details
    private void saveAndLogLenderDetails(LendingApplicationLenderDetails lald, LendingApplication lendingApplication, String message) {
        laldDao.save(lald);
        log.info("{} for applicationId: {}, merchantId: {}, leadStatus: {}, leadSubStatus: {}", message, lendingApplication.getId(), lendingApplication.getMerchantId(), lald.getLeadStatus(), lald.getLeadSubStatus());
    }
}