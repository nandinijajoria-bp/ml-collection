package com.bharatpe.lending.lendingplatform.nbfc.service.workflow;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LeadSubStatus;
import com.bharatpe.lending.lendingplatform.nbfc.client.LendingPlatformClient;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.LenderBaseRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.UpdateLeadRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LenderApiResponse;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.UpdateLeadResponse;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.request.UpdateLeadRequestBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.database.LendingApplicationLenderDetailsService;
import com.bharatpe.lending.lendingplatform.nbfc.util.WorkflowUtil;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import static com.bharatpe.lending.common.enums.LenderAssociationStatus.UPDATE_LEAD_FAILED;
import static com.bharatpe.lending.lendingplatform.nbfc.constants.WorkflowName.UPDATE_LEAD_WORKFLOW;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.LeadStatus.UPDATE_LEAD;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.Lender.TRILLIONLOANS;

@Service
@Slf4j
@RequiredArgsConstructor
public class UpdateLeadWorkflow implements Workflow {
    private final LendingPlatformClient lendingPlatformClient;
    private final UpdateLeadRequestBuilder updateLeadRequestBuilder;
    @Lazy
    private final NbfcUtils nbfcUtils;
    private final LendingApplicationLenderDetailsService lendingApplicationLenderDetailsService;
    private final WorkflowUtil workflowUtil;


    @Override
    public void invoke(String applicationId) {
        LendingApplication lendingApplication = workflowUtil.getLendingApplication(applicationId);
        LendingApplicationLenderDetails lald = workflowUtil.getLendingApplicationLenderDetails(applicationId, TRILLIONLOANS.name());
        lald.setLeadStatus(UPDATE_LEAD.name());
        lald.setLeadSubStatus(LeadSubStatus.PENDING);
        lendingApplicationLenderDetailsService.save(lald);
        LenderBaseRequest<UpdateLeadRequest> updateLeadRequest = getUpdateLeadRequest(lendingApplication);
        if (ObjectUtils.isEmpty(updateLeadRequest)) {
            log.warn("Update lead request is empty for application id {}", applicationId);
            lald.setLeadSubStatus(LeadSubStatus.REQUEST_CREATION_FAILED);
            nbfcUtils.modifyLender(lendingApplication, lald, UPDATE_LEAD_FAILED);
            return;
        }
        invokeUpdateLead(applicationId, lendingApplication, lald, updateLeadRequest);
    }

    @Override
    public String getWorkflowName() {
        return UPDATE_LEAD_WORKFLOW;
    }

    private void invokeUpdateLead(String applicationId, LendingApplication lendingApplication, LendingApplicationLenderDetails lald,
                                  LenderBaseRequest<UpdateLeadRequest> updateLeadRequest) {
        LenderApiResponse<UpdateLeadResponse> response = lendingPlatformClient.initiateUpdateLead(updateLeadRequest);
        processUpdateLeadResponse(applicationId, lendingApplication, lald, response);
    }

    private void processUpdateLeadResponse(String applicationId, LendingApplication lendingApplication, LendingApplicationLenderDetails lald,
                                           LenderApiResponse<UpdateLeadResponse> response) {
        if (ObjectUtils.isEmpty(response) || !response.isSuccess() || !isUpdateLeadResponseDataSuccess(response)) {
            log.info("Update lead response failed for application id {}", applicationId);
            lald.setLeadSubStatus(LeadSubStatus.FAILED);
            nbfcUtils.modifyLender(lendingApplication, lald, UPDATE_LEAD_FAILED);
            return;
        }
        log.info("Update lead response success for application id {}", applicationId);
        lald.setLeadSubStatus(LeadSubStatus.SUCCESS);
        lendingApplicationLenderDetailsService.save(lald);
    }

    private boolean isUpdateLeadResponseDataSuccess(LenderApiResponse<UpdateLeadResponse> response) {
        return !ObjectUtils.isEmpty(response.getData());
    }

    private LenderBaseRequest<UpdateLeadRequest> getUpdateLeadRequest(LendingApplication lendingApplication) {
        UpdateLeadRequest updateLeadRequest = updateLeadRequestBuilder
                .buildRequest(lendingApplication);
        return LenderBaseRequest.<UpdateLeadRequest>builder()
                .applicationId(String.valueOf(lendingApplication.getId()))
                .customerId(String.valueOf(lendingApplication.getMerchantId()))
                .lender(Lender.valueOf(lendingApplication.getLender()))
                .data(updateLeadRequest)
                .build();
    }
}
