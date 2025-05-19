package com.bharatpe.lending.lendingplatform.nbfc.service.workflow;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LeadSubStatus;
import com.bharatpe.lending.lendingplatform.nbfc.client.LendingPlatformClient;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.LenderBaseRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.LoanDocumentUploadRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LenderApiResponse;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LoanDocumentUploadResponse;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.request.LoanDocumentUploadRequestBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.database.LendingApplicationLenderDetailsService;
import com.bharatpe.lending.lendingplatform.nbfc.util.WorkflowUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;

import static com.bharatpe.lending.lendingplatform.nbfc.constants.WorkflowName.LOAN_DOCUMENT_WORKFLOW;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.LeadStatus.LOAN_DOCUMENT;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.Lender.TRILLIONLOANS;

@Service
@Slf4j
@RequiredArgsConstructor
public class LoanDocumentWorkflow implements Workflow {
    private final LendingPlatformClient lendingPlatformClient;
    private final LoanDocumentUploadRequestBuilder loanDocumentRequestBuilder;
    private final LendingApplicationLenderDetailsService lendingApplicationLenderDetailsService;
    private final WorkflowUtil workflowUtil;


    @Override
    public void invoke(String applicationId) {
        LendingApplication lendingApplication = workflowUtil.getLendingApplication(applicationId);
        LendingApplicationLenderDetails lald = workflowUtil.getLendingApplicationLenderDetails(applicationId, TRILLIONLOANS.name());
        lald.setLeadStatus(LOAN_DOCUMENT.name());
        lald.setLeadSubStatus(LeadSubStatus.PENDING);
        lendingApplicationLenderDetailsService.save(lald);
        LenderBaseRequest<LoanDocumentUploadRequest> loanDocumentUploadRequest = getLoanDocumentUploadRequest(lendingApplication);
        if (ObjectUtils.isEmpty(loanDocumentUploadRequest)) {
            log.warn("Loan document upload request is empty for application id {}", applicationId);
            lald.setLeadSubStatus(LeadSubStatus.REQUEST_CREATION_FAILED);
            lendingApplicationLenderDetailsService.save(lald);
            return;
        }
        invokeDocumentUpload(applicationId, lendingApplication, lald, loanDocumentUploadRequest);
    }

    @Override
    public String getWorkflowName() {
        return LOAN_DOCUMENT_WORKFLOW;
    }

    private void invokeDocumentUpload(String applicationId, LendingApplication lendingApplication, LendingApplicationLenderDetails lald,
                                      LenderBaseRequest<LoanDocumentUploadRequest> request) {
        LenderApiResponse<LoanDocumentUploadResponse> response = lendingPlatformClient.initateLoanDocUpload(request);
        processLoanDocumentUploadResponse(applicationId, lendingApplication, lald, response);
    }

    private void processLoanDocumentUploadResponse(String applicationId, LendingApplication lendingApplication, LendingApplicationLenderDetails lald,
                                                   LenderApiResponse<LoanDocumentUploadResponse> response) {
        if (ObjectUtils.isEmpty(response) || !response.isSuccess() || !isDocUploadResponseDataSuccess(response)) {
            log.info("Doc upload response failed for application id {}", applicationId);
            lald.setLeadSubStatus(LeadSubStatus.FAILED);
            lendingApplicationLenderDetailsService.save(lald);
            return;
        }
        log.info("Doc upload response success for application id {}", applicationId);
        lald.setLeadSubStatus(LeadSubStatus.SUCCESS);
        lendingApplicationLenderDetailsService.save(lald);
    }

    private boolean isDocUploadResponseDataSuccess(LenderApiResponse<LoanDocumentUploadResponse> response) {
        return !ObjectUtils.isEmpty(response.getData());
    }

    private LenderBaseRequest<LoanDocumentUploadRequest> getLoanDocumentUploadRequest(LendingApplication lendingApplication) {
        LoanDocumentUploadRequest loanDocumentUploadRequest;
        try {
            loanDocumentUploadRequest = loanDocumentRequestBuilder
                    .buildRequest(lendingApplication);
        } catch (Exception e) {
            log.error("Error while creating loan document request for applicationId={}, error:{}",
                    lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
            return null;
        }
        return LenderBaseRequest.<LoanDocumentUploadRequest>builder()
                .applicationId(String.valueOf(lendingApplication.getId()))
                .customerId(String.valueOf(lendingApplication.getMerchantId()))
                .lender(Lender.valueOf(lendingApplication.getLender()))
                .data(loanDocumentUploadRequest)
                .build();
    }
}
