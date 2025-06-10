package com.bharatpe.lending.lendingplatform.nbfc.service.workflow;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LeadSubStatus;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.lendingplatform.nbfc.client.LendingPlatformClient;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.BRERequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.LenderBaseRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.BREResponse;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LenderApiResponse;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.request.BRERequestBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.database.LendingApplicationDetailsService;
import com.bharatpe.lending.lendingplatform.nbfc.service.database.LendingApplicationLenderDetailsService;
import com.bharatpe.lending.lendingplatform.nbfc.util.WorkflowUtil;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import static com.bharatpe.lending.common.enums.LenderAssociationStatus.RISK_FAILED;
import static com.bharatpe.lending.lendingplatform.nbfc.constants.BREStatus.INITIATED;
import static com.bharatpe.lending.lendingplatform.nbfc.constants.WorkflowName.BRE_WORKFLOW;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.LeadStatus.BRE;

@Service
@Slf4j
@RequiredArgsConstructor
public class BreWorkflow implements Workflow {
    private final LendingPlatformClient lendingPlatformClient;
    private final BRERequestBuilder breRequestBuilder;
    @Lazy
    private final NbfcUtils nbfcUtils;
    private final WorkflowUtil workflowUtil;
    private final LendingApplicationLenderDetailsService lendingApplicationLenderDetailsService;
    private final LendingApplicationDetailsService lendingApplicationDetailsService;

    @Override
    public void invoke(String applicationId) {
        LendingApplication lendingApplication = workflowUtil.getLendingApplication(applicationId);
        LendingApplicationLenderDetails lald = workflowUtil.getLendingApplicationLenderDetails(applicationId, lendingApplication.getLender());
        LendingApplicationDetails lendingApplicationDetails = workflowUtil.getLendingApplicationDetails(applicationId);
        lald.setLeadStatus(BRE.name());
        lald.setLeadSubStatus(LeadSubStatus.PENDING);
        lendingApplicationLenderDetailsService.save(lald);
        lendingApplicationDetails.setStage(LenderAssociationStages.BRE.name());
        lendingApplicationDetailsService.save(lendingApplicationDetails);
        LenderBaseRequest<BRERequest> breRequest = getBRERequest(lendingApplication);
        if (ObjectUtils.isEmpty(breRequest)) {
            log.warn("BRE request is empty for application id {}", applicationId);
            lald.setLeadSubStatus(LeadSubStatus.REQUEST_CREATION_FAILED);
            nbfcUtils.modifyLender(lendingApplication, lald, RISK_FAILED);
            return;
        }
        invokeBRE(applicationId, lendingApplication, lald, breRequest);
    }

    @Override
    public String getWorkflowName() {
        return BRE_WORKFLOW;
    }

    private void invokeBRE(String applicationId, LendingApplication lendingApplication, LendingApplicationLenderDetails lald, LenderBaseRequest<BRERequest> breRequest) {
        LenderApiResponse<BREResponse> response = lendingPlatformClient.initiateBRE(breRequest);
        processBREResponse(applicationId, lendingApplication, lald, response);
    }

    private void processBREResponse(String applicationID, LendingApplication lendingApplication,
                                    LendingApplicationLenderDetails lald, LenderApiResponse<BREResponse> response) {
        if (ObjectUtils.isEmpty(response) || !response.isSuccess() || !isBREResponseDataSuccess(response)){
            log.info("BRE response failure for application id {}", applicationID);
            lald.setLeadSubStatus(LeadSubStatus.FAILED);
            nbfcUtils.modifyLender(lendingApplication, lald, RISK_FAILED);
            return;
        }
        log.info("BRE response success for application id {}", applicationID);
        lald.setLeadSubStatus(LeadSubStatus.CALLBACK_PENDING);
        lendingApplicationLenderDetailsService.save(lald);
    }
    private boolean isBREResponseDataSuccess(LenderApiResponse<BREResponse> response) {
        return INITIATED.equalsIgnoreCase(response.getData().getStatus());
    }

    private LenderBaseRequest<BRERequest> getBRERequest(LendingApplication lendingApplication) {
        BRERequest breRequest = breRequestBuilder.buildRequest(lendingApplication);
        return LenderBaseRequest.<BRERequest>builder()
                .applicationId(String.valueOf(lendingApplication.getId()))
                .customerId(String.valueOf(lendingApplication.getMerchantId()))
                .lender(Lender.valueOf(lendingApplication.getLender()))
                .data(breRequest)
                .build();
    }
}
