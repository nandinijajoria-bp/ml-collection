package com.bharatpe.lending.lendingplatform.nbfc.service.workflow;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LeadSubStatus;
import com.bharatpe.lending.lendingplatform.nbfc.client.LendingPlatformClient;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.KYCDocumentUploadRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.LenderBaseRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.KYCDocumentUploadResponse;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LenderApiResponse;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.request.KYCDocumentUploadRequestBuilder;
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
import static com.bharatpe.lending.lendingplatform.nbfc.constants.WorkflowName.KYC_DOCUMENT_WORKFLOW;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.LeadStatus.KYC_DOCUMENT;

@Service
@Slf4j
@RequiredArgsConstructor
public class KYCDocumentWorkflow implements Workflow {
    private final LendingPlatformClient lendingPlatformClient;
    private final KYCDocumentUploadRequestBuilder kycDocumentUploadRequestBuilder;
    private final LendingApplicationLenderDetailsService lendingApplicationLenderDetailsService;
    @Lazy
    private final NbfcUtils nbfcUtils;
    private final WorkflowUtil workflowUtil;

    @Override
    public void invoke(String applicationId) {
        LendingApplication lendingApplication = workflowUtil.getLendingApplication(applicationId);
        LendingApplicationLenderDetails lald = workflowUtil.getLendingApplicationLenderDetails(applicationId, lendingApplication.getLender());
        lald.setLeadStatus(KYC_DOCUMENT.name());
        lald.setLeadSubStatus(LeadSubStatus.PENDING);
        lendingApplicationLenderDetailsService.save(lald);
        LenderBaseRequest<KYCDocumentUploadRequest> kycDocumentUploadRequest = getKYCDocumentUploadRequest(lendingApplication);
        if (ObjectUtils.isEmpty(kycDocumentUploadRequest)) {
            log.warn("KYC document upload request is empty");
            lald.setLeadSubStatus(LeadSubStatus.REQUEST_CREATION_FAILED);
            nbfcUtils.modifyLender(lendingApplication, lald, KYC_FAILED);
            return;
        }
        invokeKYCDocumentUpload(applicationId, lendingApplication, lald, kycDocumentUploadRequest);
    }

    @Override
    public String getWorkflowName() {
        return KYC_DOCUMENT_WORKFLOW;
    }

    private void invokeKYCDocumentUpload(String applicationId, LendingApplication lendingApplication,
                                         LendingApplicationLenderDetails lald,
                                         LenderBaseRequest<KYCDocumentUploadRequest> kycDocumentUploadRequest) {
        LenderApiResponse<KYCDocumentUploadResponse> response = lendingPlatformClient.initiateKYCDocumentUpload(kycDocumentUploadRequest);
        processKYCDocumentUploadResponse(applicationId, lendingApplication, lald, response);
    }

    private void processKYCDocumentUploadResponse(String applicationId, LendingApplication lendingApplication,
                                                  LendingApplicationLenderDetails lald,
                                                  LenderApiResponse<KYCDocumentUploadResponse> response) {
        if (ObjectUtils.isEmpty(response) || !response.isSuccess() || !isKYCDocUploadResponseDataSuccess(response)) {
            log.info("KYC doc upload response failure for application id {}", applicationId);
            lald.setLeadSubStatus(LeadSubStatus.FAILED);
            nbfcUtils.modifyLender(lendingApplication, lald, KYC_FAILED);
            return;
        }
        log.info("KYC doc upload response success for application id {}", applicationId);
        lald.setLeadSubStatus(LeadSubStatus.CALLBACK_PENDING);
        lendingApplicationLenderDetailsService.save(lald);
    }

    private boolean isKYCDocUploadResponseDataSuccess(LenderApiResponse<KYCDocumentUploadResponse> response) {
        return !ObjectUtils.isEmpty(response.getData());
    }

    private LenderBaseRequest<KYCDocumentUploadRequest> getKYCDocumentUploadRequest(LendingApplication lendingApplication) {
        KYCDocumentUploadRequest kycDocumentUploadRequest;
        try {
            kycDocumentUploadRequest = kycDocumentUploadRequestBuilder
                    .buildRequest(lendingApplication);
        } catch (Exception e) {
            log.error("Error while creating Kyc Document request for applicationId={}, error:{}",
                    lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
            return null;
        }
        return LenderBaseRequest.<KYCDocumentUploadRequest>builder()
                .applicationId(String.valueOf(lendingApplication.getId()))
                .customerId(String.valueOf(lendingApplication.getMerchantId()))
                .lender(Lender.valueOf(lendingApplication.getLender()))
                .data(kycDocumentUploadRequest)
                .build();
    }
}
