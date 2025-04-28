package com.bharatpe.lending.lendingplatform.nbfc.service.workflow;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LeadSubStatus;
import com.bharatpe.lending.lendingplatform.nbfc.client.LendingPlatformClient;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.LenderBaseRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.NachRegistrationRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LenderApiResponse;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.NachRegistrationResponse;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.request.NachRegistrationRequestBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.database.LendingApplicationDetailsService;
import com.bharatpe.lending.lendingplatform.nbfc.service.database.LendingApplicationLenderDetailsService;
import com.bharatpe.lending.lendingplatform.nbfc.util.WorkflowUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;

import static com.bharatpe.lending.common.enums.LenderAssociationStages.DRAWDOWN;
import static com.bharatpe.lending.lendingplatform.nbfc.constants.WorkflowName.NACH_WORKFLOW;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.LeadStatus.NACH;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.Lender.TRILLIONLOANS;

@Service
@Slf4j
@RequiredArgsConstructor
public class NachWorkflow implements Workflow {
    private final LendingPlatformClient lendingPlatformClient;
    private final NachRegistrationRequestBuilder nachRegistrationRequestBuilder;
    private final WorkflowUtil workflowUtil;
    private final LendingApplicationDetailsService lendingApplicationDetailsService;
    private final LendingApplicationLenderDetailsService lendingApplicationLenderDetailsService;


    @Override
    public void invoke(String applicationId) {
        LendingApplication lendingApplication = workflowUtil.getLendingApplication(applicationId);
        LendingApplicationLenderDetails lald = workflowUtil.getLendingApplicationLenderDetails(applicationId, TRILLIONLOANS.name());
        lald.setLeadStatus(NACH.name());
        lald.setLeadSubStatus(LeadSubStatus.PENDING);
        lendingApplicationLenderDetailsService.save(lald);
        LenderBaseRequest<NachRegistrationRequest> nachRegistrationRequest = getNachRegistrationRequest(lendingApplication);
        if (ObjectUtils.isEmpty(nachRegistrationRequest)) {
            log.error("Nach request is empty for application id {}", applicationId);
            lald.setLeadSubStatus(LeadSubStatus.REQUEST_CREATION_FAILED);
            lendingApplicationLenderDetailsService.save(lald);
            return;
        }
        invokeNachRegistration(applicationId, lendingApplication, lald, nachRegistrationRequest);
    }

    @Override
    public String getWorkflowName() {
        return NACH_WORKFLOW;
    }

    private void invokeNachRegistration(String applicationId, LendingApplication lendingApplication, LendingApplicationLenderDetails lald,
                                        LenderBaseRequest<NachRegistrationRequest> nachRegistrationRequest) {
        LenderApiResponse<NachRegistrationResponse> response = lendingPlatformClient.initiateNach(nachRegistrationRequest);
        processNachRegistrationResponse(applicationId, lendingApplication, lald, response);
    }

    private void processNachRegistrationResponse(String applicationId, LendingApplication lendingApplication, LendingApplicationLenderDetails lald,
                                                 LenderApiResponse<NachRegistrationResponse> response) {
        if (ObjectUtils.isEmpty(response) || !response.isSuccess() || !isNachResponseDataSuccess(response)) {
            log.info("Nach registration response failed for application id {}", applicationId);
            lald.setLeadSubStatus(LeadSubStatus.FAILED);
            lendingApplicationLenderDetailsService.save(lald);
            return;
        }
        log.info("Nach registration response success for application id {}", applicationId);
        updateLad(applicationId);
        updateLald(lald);
    }

    private void updateLad(String applicationId) {
        LendingApplicationDetails lendingApplicationDetails = workflowUtil.getLendingApplicationDetails(applicationId);
        lendingApplicationDetails.setStage(DRAWDOWN.name());
        lendingApplicationDetailsService.save(lendingApplicationDetails);
    }

    private void updateLald(LendingApplicationLenderDetails lald) {
        lald.setStage(DRAWDOWN.name());
        lald.setLeadSubStatus(LeadSubStatus.SUCCESS);
        lendingApplicationLenderDetailsService.save(lald);
    }

    private boolean isNachResponseDataSuccess(LenderApiResponse<NachRegistrationResponse> response) {
        return !ObjectUtils.isEmpty(response.getData());
    }

    private LenderBaseRequest<NachRegistrationRequest> getNachRegistrationRequest(LendingApplication lendingApplication) {
        NachRegistrationRequest nachRegistrationRequest;
        try {
            nachRegistrationRequest = nachRegistrationRequestBuilder
                    .buildRequest(lendingApplication);
        } catch (Exception e) {
            log.error("Error while creating NachRegister request for applicationId={}, error:{}",
                    lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
            return null;
        }
        return LenderBaseRequest.<NachRegistrationRequest>builder()
                .applicationId(String.valueOf(lendingApplication.getId()))
                .customerId(String.valueOf(lendingApplication.getMerchantId()))
                .lender(Lender.valueOf(lendingApplication.getLender()))
                .data(nachRegistrationRequest)
                .build();
    }
}
