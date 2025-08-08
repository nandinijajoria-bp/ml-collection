package com.bharatpe.lending.lendingplatform.nbfc.service.workflow;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationVkycDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingApplicationVkycDetails;
import com.bharatpe.lending.common.enums.LeadSubStatus;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.VkycStatus;
import com.bharatpe.lending.dto.vkyc.response.VKycInitiateResponseDto;
import com.bharatpe.lending.lendingplatform.nbfc.client.LendingPlatformClient;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.LenderBaseRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.VKYCRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LenderApiResponse;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.VKYCResponse;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.request.VKYCRequestBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.database.LendingApplicationDetailsService;
import com.bharatpe.lending.lendingplatform.nbfc.service.database.LendingApplicationLenderDetailsService;
import com.bharatpe.lending.lendingplatform.nbfc.util.WorkflowUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Date;

import static com.bharatpe.lending.lendingplatform.nbfc.enums.LeadStatus.VKYC;

@Slf4j
@Service
@RequiredArgsConstructor
public class VKYCWorkflow {
    private final LendingPlatformClient lendingPlatformClient;
    private final VKYCRequestBuilder vkycRequestBuilder;
    private final LendingApplicationLenderDetailsService lendingApplicationLenderDetailsService;
    private final LendingApplicationDetailsService lendingApplicationDetailsService;
    private final WorkflowUtil workflowUtil;
    private final LendingApplicationVkycDetailsDao lendingApplicationVkycDetailsDao;

    public VKycInitiateResponseDto invoke(String applicationId) {
        LendingApplication lendingApplication = workflowUtil.getLendingApplication(applicationId);
        LendingApplicationLenderDetails lald = workflowUtil.getLendingApplicationLenderDetails(applicationId, lendingApplication.getLender());
        setLaldPending(lald);
        LenderBaseRequest<VKYCRequest> vkycRequest = buildVkycRequest(lendingApplication);
        if (ObjectUtils.isEmpty(vkycRequest)) {
            log.info("VKYC request creation failed: request is empty for applicationId={}, lender={}", applicationId, lendingApplication.getLender());
            setLaldSubStatus(lald, LeadSubStatus.REQUEST_CREATION_FAILED);
            return VKycInitiateResponseDto.builder().build();
        }
        return invokeVkyc(applicationId, lendingApplication, lald, vkycRequest);
    }

    private VKycInitiateResponseDto invokeVkyc(String applicationId, LendingApplication lendingApplication, LendingApplicationLenderDetails lald,
                                               LenderBaseRequest<VKYCRequest> request) {
        LenderApiResponse<VKYCResponse> response = lendingPlatformClient.initateVkyc(request);
        return processVkycResponse(applicationId, lendingApplication, lald, response);
    }

    private VKycInitiateResponseDto processVkycResponse(String applicationId, LendingApplication lendingApplication, LendingApplicationLenderDetails lald,
                                                        LenderApiResponse<VKYCResponse> response) {
        if (ObjectUtils.isEmpty(response) || !response.isSuccess() || !isVkycResponseDataSuccess(response)) {
            log.info("VKYC response failed for application id {}, response {}", applicationId, response);

            lald.setLeadSubStatus(LeadSubStatus.FAILED);
            lendingApplicationLenderDetailsService.save(lald);
            return null;
        }
        log.info("VKYC api response success for application id {}", applicationId);

        LendingApplicationVkycDetails vkycDetails = lendingApplicationVkycDetailsDao
                .findByApplicationIdAndLender(lendingApplication.getId(), lendingApplication.getLender())
                .orElse(null);
        if (!ObjectUtils.isEmpty(vkycDetails)) {
            updateVkycDetailsOnInitiation(vkycDetails, response.getData().getVkycWaitPageUrl());
        } else {
            log.error("No VKYC details found for applicationId={}, lender={}", lendingApplication.getId(), lendingApplication.getLender());
        }
        updateLaldStage(lald);
        log.info("VKYC workflow invoked successfully for applicationId={}, lender={}, response Data={}", applicationId, lendingApplication.getLender(), response.getData());
        return VKycInitiateResponseDto.builder()
                .sessionUrl(response.getData().getVkycWaitPageUrl())
                .sessionId(response.getData().getTrackingId())
                .leadId(response.getData().getLeadId())
                .build();
    }

    private void updateVkycDetailsOnInitiation(LendingApplicationVkycDetails vkycDetails, String vkycWaitPageUrl) {
        vkycDetails.setSessionUrl(vkycWaitPageUrl);
        vkycDetails.setStatus(VkycStatus.VKYC_IN_PROGRESS);
        lendingApplicationVkycDetailsDao.save(vkycDetails);
        log.info("VKYC details updated for applicationId={}, vkycDetails={}", vkycDetails.getApplicationId(), vkycDetails);
    }

    private void updateLaldStage(LendingApplicationLenderDetails lald) {
        lald.setLeadSubStatus(LeadSubStatus.CALLBACK_PENDING);
        lendingApplicationLenderDetailsService.save(lald);
        log.info("LendingApplicationDetails updated with Stage: {}, LeadSubStatus: {}", lald.getStage(), lald.getLeadSubStatus());
    }

    private boolean isVkycResponseDataSuccess(LenderApiResponse<VKYCResponse> response) {
        return response.getData() != null;
    }

    private LenderBaseRequest<VKYCRequest> buildVkycRequest(LendingApplication lendingApplication) {
        try {
            VKYCRequest vkycRequest = vkycRequestBuilder.buildRequest(lendingApplication);
            return LenderBaseRequest.<VKYCRequest>builder()
                    .applicationId(String.valueOf(lendingApplication.getId()))
                    .customerId(String.valueOf(lendingApplication.getMerchantId()))
                    .lender(Lender.valueOf(lendingApplication.getLender()))
                    .data(vkycRequest)
                    .build();
        } catch (Exception e) {
            log.error("Error while creating VKYC request for applicationId={}, error:{}",
                    lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
            return null;
        }
    }

    private void setLaldPending(LendingApplicationLenderDetails lald) {
        lald.setLeadStatus(VKYC.name());
        lald.setLeadSubStatus(LeadSubStatus.PENDING);
        lendingApplicationLenderDetailsService.save(lald);
        log.info("LendingApplicationLenderDetails updated to VKYC pending for applicationId={}, lender={}", lald.getApplicationId(), lald.getLender());
    }

    private void setLaldSubStatus(LendingApplicationLenderDetails lald, LeadSubStatus subStatus) {
        lald.setLeadSubStatus(subStatus);
        lendingApplicationLenderDetailsService.save(lald);
        log.info("LendingApplicationLenderDetails updated with LeadSubStatus: {}", subStatus);
    }
}
