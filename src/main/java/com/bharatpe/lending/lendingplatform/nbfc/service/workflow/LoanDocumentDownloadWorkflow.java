package com.bharatpe.lending.lendingplatform.nbfc.service.workflow;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LeadSubStatus;
import com.bharatpe.lending.lendingplatform.nbfc.client.LendingPlatformClient;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.LenderBaseRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.LoanDocumentDownloadRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LenderApiResponse;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LoanDocumentDownloadResponse;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.request.LoanDocumentDownloadRequestBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.database.LendingApplicationLenderDetailsService;
import com.bharatpe.lending.lendingplatform.nbfc.util.WorkflowUtil;
import com.bharatpe.lending.loanV3.utils.DocUploadUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import static com.bharatpe.lending.lendingplatform.nbfc.constants.WorkflowName.LOAN_DOCUMENT_DOWNLOAD_WORKFLOW;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.LeadStatus.LOAN_DOCUMENT_DOWNLOAD;

@Service
@Slf4j
@RequiredArgsConstructor
public class LoanDocumentDownloadWorkflow implements Workflow {
    private final LendingPlatformClient lendingPlatformClient;
    private final LoanDocumentDownloadRequestBuilder loanDocumentRequestBuilder;
    private final LendingApplicationLenderDetailsService lendingApplicationLenderDetailsService;
    private final WorkflowUtil workflowUtil;
    private final DocUploadUtils docUploadUtils;


    @Override
    public void invoke(String applicationId) {
        LendingApplication lendingApplication = workflowUtil.getLendingApplication(applicationId);
        LendingApplicationLenderDetails lald = workflowUtil.getLendingApplicationLenderDetails(applicationId, lendingApplication.getLender());
        lald.setLeadStatus(LOAN_DOCUMENT_DOWNLOAD.name());
        lald.setLeadSubStatus(LeadSubStatus.PENDING);
        lendingApplicationLenderDetailsService.save(lald);
        LenderBaseRequest<LoanDocumentDownloadRequest> loanDocumentDownloadRequest = getLoanDocumentDownloadRequest(lendingApplication);
        if (ObjectUtils.isEmpty(loanDocumentDownloadRequest)) {
            log.warn("Loan document download request is empty for application id {}", applicationId);
            lald.setLeadSubStatus(LeadSubStatus.REQUEST_CREATION_FAILED);
            lendingApplicationLenderDetailsService.save(lald);
            return;
        }
        invokeDocumentDownload(applicationId, lendingApplication, lald, loanDocumentDownloadRequest);
    }

    @Override
    public String getWorkflowName() {
        return LOAN_DOCUMENT_DOWNLOAD_WORKFLOW;
    }

    private void invokeDocumentDownload(String applicationId, LendingApplication lendingApplication, LendingApplicationLenderDetails lald,
                                        LenderBaseRequest<LoanDocumentDownloadRequest> request) {
        LenderApiResponse<LoanDocumentDownloadResponse> response = lendingPlatformClient.initateLoanDocDownload(request);
        processLoanDocumentDownloadResponse(applicationId, lendingApplication, lald, response);
    }

    private LenderBaseRequest<LoanDocumentDownloadRequest> getLoanDocumentDownloadRequest(LendingApplication lendingApplication) {
        LoanDocumentDownloadRequest loanDocumentDownloadRequest;
        try {
            loanDocumentDownloadRequest = loanDocumentRequestBuilder
                    .buildRequest(lendingApplication);
        } catch (Exception e) {
            log.error("Error while creating loan document request for applicationId={}, error:{}",
                    lendingApplication.getId(), e.getMessage(), e);
            return null;
        }
        return LenderBaseRequest.<LoanDocumentDownloadRequest>builder()
                .applicationId(String.valueOf(lendingApplication.getId()))
                .customerId(String.valueOf(lendingApplication.getMerchantId()))
                .lender(Lender.valueOf(lendingApplication.getLender()))
                .data(loanDocumentDownloadRequest)
                .build();
    }

    public void processLoanDocumentDownloadResponse(String applicationId, LendingApplication lendingApplication, LendingApplicationLenderDetails lald,
                                                    LenderApiResponse<LoanDocumentDownloadResponse> response) {
        if (ObjectUtils.isEmpty(response) || !response.isSuccess() || response.getData() == null) {
            log.info("Doc download response failed for application id {}", applicationId);
            lald.setLeadSubStatus(LeadSubStatus.FAILED);
            lendingApplicationLenderDetailsService.save(lald);
            return;
        }
        log.info("Doc download response success for application id {}", applicationId);
        docUploadUtils.saveESignedDocs(lendingApplication.getId(), response.getData().getSignedKFSUrl(), response.getData().getSignedSanctionUrl());
        lald.setLeadSubStatus(LeadSubStatus.SUCCESS);
        lendingApplicationLenderDetailsService.save(lald);
    }
}
