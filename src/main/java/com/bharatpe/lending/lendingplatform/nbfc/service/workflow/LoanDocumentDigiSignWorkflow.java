package com.bharatpe.lending.lendingplatform.nbfc.service.workflow;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LeadSubStatus;
import com.bharatpe.lending.lendingplatform.nbfc.client.LendingPlatformClient;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.LenderBaseRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.LoanDocumentDigiSignRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LenderApiResponse;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LoanDocumentDigiSignResponse;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.request.LoanDocumentDigiSignRequestBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.database.LendingApplicationLenderDetailsService;
import com.bharatpe.lending.lendingplatform.nbfc.util.WorkflowUtil;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import static com.bharatpe.lending.common.enums.LenderAssociationStatus.DIGI_SIGN_FAILED;
import static com.bharatpe.lending.lendingplatform.nbfc.constants.WorkflowName.LOAN_DOCUMENT_DIGI_SIGN_WORKFLOW;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.LeadStatus.DIGI_SIGN;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.Lender.TRILLIONLOANS;

@Service
@Slf4j
@RequiredArgsConstructor
public class LoanDocumentDigiSignWorkflow implements Workflow {
    private final LendingPlatformClient lendingPlatformClient;
    private final LoanDocumentDigiSignRequestBuilder loanDocumentDigiSignRequestBuilder;
    @Lazy
    private final NbfcUtils nbfcUtils;
    private final LendingApplicationLenderDetailsService lendingApplicationLenderDetailsService;
    private final WorkflowUtil workflowUtil;


    @Override
    public void invoke(String applicationId) {
        LendingApplication lendingApplication = workflowUtil.getLendingApplication(applicationId);
        LendingApplicationLenderDetails lald = workflowUtil.getLendingApplicationLenderDetails(applicationId, TRILLIONLOANS.name());
        lald.setLeadStatus(DIGI_SIGN.name());
        lald.setLeadSubStatus(LeadSubStatus.PENDING);
        lendingApplicationLenderDetailsService.save(lald);
        LenderBaseRequest<LoanDocumentDigiSignRequest> loanDocumentDigiSignRequest = getLoanDocumentDigiSignRequest(lendingApplication);
        if (ObjectUtils.isEmpty(loanDocumentDigiSignRequest)) {
            log.warn("Loan document digi sign request is empty for application id {}", applicationId);
            lald.setLeadSubStatus(LeadSubStatus.REQUEST_CREATION_FAILED);
            nbfcUtils.modifyLender(lendingApplication, lald, DIGI_SIGN_FAILED);
            return;
        }
        invokeLoanDocumentDigiSign(applicationId, lendingApplication, lald, loanDocumentDigiSignRequest);
    }

    @Override
    public String getWorkflowName() {
        return LOAN_DOCUMENT_DIGI_SIGN_WORKFLOW;
    }

    private void invokeLoanDocumentDigiSign(String applicationId, LendingApplication lendingApplication, LendingApplicationLenderDetails lald,
                                            LenderBaseRequest<LoanDocumentDigiSignRequest> loanDocumentDigiSignRequest) {
        LenderApiResponse<LoanDocumentDigiSignResponse> response = lendingPlatformClient.initiateDigiSign(loanDocumentDigiSignRequest);
        processLoanDocumentDigiSignResponse(applicationId, lendingApplication, lald, response);
    }

    private void processLoanDocumentDigiSignResponse(String applicationId, LendingApplication lendingApplication, LendingApplicationLenderDetails lald,
                                                     LenderApiResponse<LoanDocumentDigiSignResponse> response) {
        if (ObjectUtils.isEmpty(response) || !response.isSuccess() || !isDigiSignResponseDataSuccess(response)) {
            log.info("Digi sign response failed for application id {}", applicationId);
            lald.setLeadSubStatus(LeadSubStatus.FAILED);
            nbfcUtils.modifyLender(lendingApplication, lald, DIGI_SIGN_FAILED);
            return;
        }
        log.info("Digi sign response success for application id {}", applicationId);
        lald.setLeadSubStatus(LeadSubStatus.SUCCESS);
        lendingApplicationLenderDetailsService.save(lald);
    }

    private boolean isDigiSignResponseDataSuccess(LenderApiResponse<LoanDocumentDigiSignResponse> response) {
        return !ObjectUtils.isEmpty(response.getData());
    }

    private LenderBaseRequest<LoanDocumentDigiSignRequest> getLoanDocumentDigiSignRequest(LendingApplication lendingApplication) {
        LoanDocumentDigiSignRequest loanDocumentDigiSignRequest = loanDocumentDigiSignRequestBuilder
                .buildRequest(lendingApplication);
        return LenderBaseRequest.<LoanDocumentDigiSignRequest>builder()
                .applicationId(String.valueOf(lendingApplication.getId()))
                .customerId(String.valueOf(lendingApplication.getMerchantId()))
                .lender(Lender.valueOf(lendingApplication.getLender()))
                .data(loanDocumentDigiSignRequest)
                .build();
    }
}
