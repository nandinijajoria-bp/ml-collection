package com.bharatpe.lending.lendingplatform.nbfc.service.workflow;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LeadSubStatus;
import com.bharatpe.lending.lendingplatform.nbfc.client.LendingPlatformClient;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.LenderBaseRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.PennyDropRegistrationRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LenderApiResponse;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.PennyDropRegistrationResponse;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.request.PennyDropRegistrationRequestBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.database.LendingApplicationLenderDetailsService;
import com.bharatpe.lending.lendingplatform.nbfc.util.WorkflowUtil;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import static com.bharatpe.lending.common.enums.LenderAssociationStatus.PENNY_DROP_FAILED;
import static com.bharatpe.lending.lendingplatform.nbfc.constants.WorkflowName.PENNY_DROP_WORKFLOW;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.LeadStatus.PENNY_DROP;

@Service
@Slf4j
@RequiredArgsConstructor
public class PennyDropWorkflow implements Workflow {
    private final LendingPlatformClient lendingPlatformClient;
    private final PennyDropRegistrationRequestBuilder pennyDropRegistrationRequestBuilder;
    private final WorkflowUtil workflowUtil;
    @Lazy
    private final NbfcUtils nbfcUtils;
    private final LendingApplicationLenderDetailsService lendingApplicationLenderDetailsService;


    @Override
    public boolean invoke(String applicationId) {
        LendingApplication lendingApplication = workflowUtil.getLendingApplication(applicationId);
        LendingApplicationLenderDetails lald = workflowUtil.getLendingApplicationLenderDetails(applicationId, lendingApplication.getLender());
        lald.setLeadStatus(PENNY_DROP.name());
        lald.setLeadSubStatus(LeadSubStatus.PENDING);
        lendingApplicationLenderDetailsService.save(lald);
        LenderBaseRequest<PennyDropRegistrationRequest> pennyDropRegistrationRequest = getPennyDropRegistrationRequest(lendingApplication);
        if (ObjectUtils.isEmpty(pennyDropRegistrationRequest)) {
            log.warn("Penny Drop request is empty for application id {}", applicationId);
            lald.setLeadSubStatus(LeadSubStatus.REQUEST_CREATION_FAILED);
            lendingApplicationLenderDetailsService.save(lald);
            return false;
        }
        return invokePennyDropRegistration(applicationId, lendingApplication, lald, pennyDropRegistrationRequest);
    }

    @Override
    public String getWorkflowName() {
        return PENNY_DROP_WORKFLOW;
    }

    private boolean invokePennyDropRegistration(String applicationId, LendingApplication lendingApplication, LendingApplicationLenderDetails lald,
                                             LenderBaseRequest<PennyDropRegistrationRequest> pennyDropRequest) {
        LenderApiResponse<PennyDropRegistrationResponse> response = lendingPlatformClient.initiatePennyDrop(pennyDropRequest);
        return processPennyDropRegistrationResponse(applicationId, lendingApplication, lald, response);
    }

    private boolean processPennyDropRegistrationResponse(String applicationId, LendingApplication lendingApplication, LendingApplicationLenderDetails lald,
                                                      LenderApiResponse<PennyDropRegistrationResponse> response) {;
        if (ObjectUtils.isEmpty(response) || !response.isSuccess() || !isPennyDropResponseDataSuccess(response)) {
            log.info("PennyDrop registration response failed for application id {}", applicationId);
            lald.setLeadSubStatus(LeadSubStatus.FAILED);
            lendingApplicationLenderDetailsService.save(lald);
            nbfcUtils.modifyLender(lendingApplication, lald, PENNY_DROP_FAILED);
            return false;
        }
        log.info("PennyDrop registration response success for application id {}", applicationId);
        updateLald(lald);
        return true;
    }

    private void updateLald(LendingApplicationLenderDetails lald) {
        lald.setLeadSubStatus(LeadSubStatus.CALLBACK_PENDING);
        lendingApplicationLenderDetailsService.save(lald);
    }

    private boolean isPennyDropResponseDataSuccess(LenderApiResponse<PennyDropRegistrationResponse> response) {
        return !ObjectUtils.isEmpty(response.getData());
    }

    private LenderBaseRequest<PennyDropRegistrationRequest> getPennyDropRegistrationRequest(LendingApplication lendingApplication) {
        PennyDropRegistrationRequest pennyDropRegistrationRequest;
        try {
            pennyDropRegistrationRequest = pennyDropRegistrationRequestBuilder
                    .buildRequest(lendingApplication);
        } catch (Exception e) {
            log.error("Error while creating PennyDrop request for applicationId={}, error:{}",
                    lendingApplication.getId(), e.getMessage(), e);
            return null;
        }
        return LenderBaseRequest.<PennyDropRegistrationRequest>builder()
                .applicationId(String.valueOf(lendingApplication.getId()))
                .customerId(String.valueOf(lendingApplication.getMerchantId()))
                .lender(Lender.valueOf(lendingApplication.getLender()))
                .data(pennyDropRegistrationRequest)
                .build();
    }
}
