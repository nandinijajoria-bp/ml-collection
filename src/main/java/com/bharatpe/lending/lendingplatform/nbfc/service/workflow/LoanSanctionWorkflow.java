package com.bharatpe.lending.lendingplatform.nbfc.service.workflow;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LeadSubStatus;
import com.bharatpe.lending.lendingplatform.nbfc.client.LendingPlatformClient;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.LenderBaseRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.LoanSanctionRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LenderApiResponse;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.request.LoanSanctionRequestBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.database.LendingApplicationLenderDetailsService;
import com.bharatpe.lending.lendingplatform.nbfc.util.WorkflowUtil;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import static com.bharatpe.lending.common.enums.LenderAssociationStatus.SANCTION_FAILED;
import static com.bharatpe.lending.lendingplatform.nbfc.constants.WorkflowName.LOAN_SANCTION_WORKFLOW;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.LeadStatus.LOAN_SANCTION;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.Lender.TRILLIONLOANS;

@Service
@Slf4j
@RequiredArgsConstructor
public class LoanSanctionWorkflow implements Workflow {
    private final LendingPlatformClient lendingPlatformClient;
    private final LoanSanctionRequestBuilder loanSanctionRequestBuilder;
    @Lazy
    private final NbfcUtils nbfcUtils;
    private final LendingApplicationLenderDetailsService lendingApplicationLenderDetailsService;
    private final WorkflowUtil workflowUtil;


    @Override
    public boolean invoke(String applicationId) {
        LendingApplication lendingApplication = workflowUtil.getLendingApplication(applicationId);
        LendingApplicationLenderDetails lald = workflowUtil.getLendingApplicationLenderDetails(applicationId, TRILLIONLOANS.name());
        lald.setLeadStatus(LOAN_SANCTION.name());
        lald.setLeadSubStatus(LeadSubStatus.PENDING);
        lendingApplicationLenderDetailsService.save(lald);
        LenderBaseRequest<LoanSanctionRequest> loanSanctionRequest = getLoanSanctionRequest(lendingApplication);
        if (ObjectUtils.isEmpty(loanSanctionRequest)) {
            log.warn("Loan sanction request is empty for application id {}", applicationId);
            lald.setLeadSubStatus(LeadSubStatus.REQUEST_CREATION_FAILED);
            nbfcUtils.modifyLender(lendingApplication, lald, SANCTION_FAILED);
            return false;
        }
        invokeLoanSanction(applicationId, lendingApplication, lald, loanSanctionRequest);
        return true;
    }

    @Override
    public String getWorkflowName() {
        return LOAN_SANCTION_WORKFLOW;
    }

    private void invokeLoanSanction(String applicationId, LendingApplication lendingApplication, LendingApplicationLenderDetails lald,
                                    LenderBaseRequest<LoanSanctionRequest> loanSanctionRequest) {
        LenderApiResponse<Boolean> response = lendingPlatformClient.initiateLoanSanction(loanSanctionRequest);
        processLoanSanctionResponse(applicationId, lendingApplication, lald, response);
    }

    private void processLoanSanctionResponse(String applicationId, LendingApplication lendingApplication, LendingApplicationLenderDetails lald,
                                             LenderApiResponse<Boolean> response) {
        if (ObjectUtils.isEmpty(response) || !response.isSuccess() || !isLoanSanctionResponseDataSuccess(response)) {
            log.info("Loan sanction response failed for application id {}", applicationId);
            lald.setLeadSubStatus(LeadSubStatus.FAILED);
            nbfcUtils.modifyLender(lendingApplication, lald, SANCTION_FAILED);
            return;
        }
        log.info("Loan sanction response success for application id {}", applicationId);
        lald.setLeadSubStatus(LeadSubStatus.SUCCESS);
        lendingApplicationLenderDetailsService.save(lald);
    }

    private boolean isLoanSanctionResponseDataSuccess(LenderApiResponse<Boolean> response) {
        return !ObjectUtils.isEmpty(response.getData());
    }

    private LenderBaseRequest<LoanSanctionRequest> getLoanSanctionRequest(LendingApplication lendingApplication) {
        LoanSanctionRequest loanSanctionRequest = null;
        try {
            loanSanctionRequest = loanSanctionRequestBuilder.buildRequest(lendingApplication);
        } catch (Exception e) {
            log.error("Error while building loan sanction request for application id {}: {}",
                    lendingApplication.getId(), e.getMessage(), e);
            return null;
        }
        return LenderBaseRequest.<LoanSanctionRequest>builder()
                .applicationId(String.valueOf(lendingApplication.getId()))
                .customerId(String.valueOf(lendingApplication.getMerchantId()))
                .lender(Lender.valueOf(lendingApplication.getLender()))
                .data(loanSanctionRequest)
                .build();
    }
}
