package com.bharatpe.lending.lendingplatform.nbfc.service.workflow;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LeadSubStatus;
import com.bharatpe.lending.lendingplatform.nbfc.client.LendingPlatformClient;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.KYCRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.LenderBaseRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.KYCResponse;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LenderApiResponse;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.request.KYCRequestBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.database.LendingApplicationLenderDetailsService;
import com.bharatpe.lending.lendingplatform.nbfc.util.WorkflowUtil;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;

import static com.bharatpe.lending.common.enums.LenderAssociationStatus.KYC_FAILED;
import static com.bharatpe.lending.lendingplatform.nbfc.constants.WorkflowName.KYC_WORKFLOW;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.LeadStatus.KYC;

@Service
@Slf4j
@RequiredArgsConstructor
public class KYCWorkflow implements Workflow {
    private final LendingPlatformClient lendingPlatformClient;
    private final KYCRequestBuilder kycRequestBuilder;
    private final LendingApplicationLenderDetailsService lendingApplicationLenderDetailsService;
    @Lazy
    private final NbfcUtils nbfcUtils;
    private final WorkflowUtil workflowUtil;

    @Override
    public boolean invoke(String applicationId) {
        LendingApplication lendingApplication = workflowUtil.getLendingApplication(applicationId);
        LendingApplicationLenderDetails lald = workflowUtil.getLendingApplicationLenderDetails(applicationId, lendingApplication.getLender());
        lald.setLeadStatus(KYC.name());
        lald.setLeadSubStatus(LeadSubStatus.PENDING);
        lendingApplicationLenderDetailsService.save(lald);
        LenderBaseRequest<KYCRequest> kycRequest = getKYCRequest(lendingApplication);
        if (ObjectUtils.isEmpty(kycRequest)) {
            log.warn("KYC request is empty for application id {}", applicationId);
            lald.setLeadSubStatus(LeadSubStatus.REQUEST_CREATION_FAILED);
            nbfcUtils.modifyLender(lendingApplication, lald, KYC_FAILED);
            return false;
        }
        return invokeKYC(applicationId, lendingApplication, lald, kycRequest);
    }

    @Override
    public String getWorkflowName() {
        return KYC_WORKFLOW;
    }

    private boolean invokeKYC(String applicationId, LendingApplication lendingApplication, LendingApplicationLenderDetails lald,
                           LenderBaseRequest<KYCRequest> kycRequest) {
        LenderApiResponse<KYCResponse> response = lendingPlatformClient.initiateKYC(kycRequest);
        return processKYCResponse(applicationId, lendingApplication, lald, response);
    }

    private boolean processKYCResponse(String applicationId, LendingApplication lendingApplication, LendingApplicationLenderDetails lald,
                                    LenderApiResponse<KYCResponse> response) {
        if (ObjectUtils.isEmpty(response) || !response.isSuccess() || !isKYCSResponseDataSuccess(response)) {
            log.info("Kyc response failed for application id {}", applicationId);
            lald.setLeadSubStatus(LeadSubStatus.FAILED);
            nbfcUtils.modifyLender(lendingApplication, lald, KYC_FAILED);
            return false;
        }
        log.info("KYC response success for application id {}", applicationId);
        lald.setLeadStatus(response.getData().getKycType().name());
        lald.setLeadSubStatus(LeadSubStatus.CALLBACK_PENDING);
        lendingApplicationLenderDetailsService.save(lald);
        return true;
    }

    private boolean isKYCSResponseDataSuccess(LenderApiResponse<KYCResponse> response) {
        return !ObjectUtils.isEmpty(response.getData());
    }

    private LenderBaseRequest<KYCRequest> getKYCRequest(LendingApplication lendingApplication) {
        KYCRequest kycRequest;
        try {
            kycRequest = kycRequestBuilder.buildRequest(lendingApplication);
        } catch (Exception e) {
            log.error("Error while creating kyc request for applicationId={}, error:{}",
                    lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
            return null;
        }
        return LenderBaseRequest.<KYCRequest>builder()
                .applicationId(String.valueOf(lendingApplication.getId()))
                .customerId(String.valueOf(lendingApplication.getMerchantId()))
                .lender(Lender.valueOf(lendingApplication.getLender()))
                .data(kycRequest)
                .build();
    }
}
