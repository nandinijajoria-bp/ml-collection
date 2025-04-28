package com.bharatpe.lending.lendingplatform.nbfc.service.workflow;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LeadSubStatus;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.lendingplatform.nbfc.client.LendingPlatformClient;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.LenderBaseRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.LoanDisbursalRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LenderApiResponse;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LoanDisbursalResponse;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.request.LoanDisbursalRequestBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.database.LendingApplicationDetailsService;
import com.bharatpe.lending.lendingplatform.nbfc.service.database.LendingApplicationLenderDetailsService;
import com.bharatpe.lending.lendingplatform.nbfc.service.database.LendingApplicationServiceV4;
import com.bharatpe.lending.lendingplatform.nbfc.util.WorkflowUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Date;

import static com.bharatpe.lending.lendingplatform.nbfc.constants.DisbursalStatus.SUCCESS;
import static com.bharatpe.lending.lendingplatform.nbfc.constants.WorkflowName.DISBURSAL_WORKFLOW;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.LeadStatus.LOAN_DISBURSAL;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.Lender.TRILLIONLOANS;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisbursalWorkflow implements Workflow {
    private final WorkflowUtil workflowUtil;
    private final LendingPlatformClient lendingPlatformClient;
    private final LoanDisbursalRequestBuilder loanDisbursalRequestBuilder;
    private final LendingApplicationLenderDetailsService lendingApplicationLenderDetailsService;
    private final LendingApplicationDetailsService lendingApplicationDetailsService;
    private final LendingApplicationServiceV4 lendingApplicationServiceV4;

    @Override
    public void invoke(String applicationId) {
        LendingApplication lendingApplication = workflowUtil.getLendingApplication(applicationId);
        LendingApplicationLenderDetails lald = workflowUtil.getLendingApplicationLenderDetails(applicationId, TRILLIONLOANS.name());
        lald.setLeadStatus(LOAN_DISBURSAL.name());
        lald.setLeadSubStatus(LeadSubStatus.PENDING);
        lendingApplicationLenderDetailsService.save(lald);
        LenderBaseRequest<LoanDisbursalRequest> loanDisbursalRequest = getLoanDisbursalRequest(lendingApplication);
        if (ObjectUtils.isEmpty(loanDisbursalRequest)) {
            log.error("Loan disbursal request is empty for application id {}", applicationId);
            lald.setLeadSubStatus(LeadSubStatus.REQUEST_CREATION_FAILED);
            lendingApplicationLenderDetailsService.save(lald);
            return;
        }
        invokeLoanDisbursal(applicationId, lendingApplication, lald, loanDisbursalRequest);
    }

    @Override
    public String getWorkflowName() {
        return DISBURSAL_WORKFLOW;
    }
    private void invokeLoanDisbursal(String applicationId, LendingApplication lendingApplication,
                                     LendingApplicationLenderDetails lald,
                                     LenderBaseRequest<LoanDisbursalRequest> loanDisbursalRequest) {
        LenderApiResponse<LoanDisbursalResponse> response = lendingPlatformClient.initiateLoanDisbursal(loanDisbursalRequest);
        processLoanDisbursalResponse(applicationId, lendingApplication, lald, response);
    }

    private void processLoanDisbursalResponse(String applicationID, LendingApplication lendingApplication,
                                              LendingApplicationLenderDetails lald,
                                              LenderApiResponse<LoanDisbursalResponse> response) {
        if (ObjectUtils.isEmpty(response) || !response.isSuccess() || !isLoanDisbursalResponseDataSuccess(response)){
            log.info("Loan disbursal response failure for application id {}", applicationID);
            lald.setLeadSubStatus(LeadSubStatus.FAILED);
            lald.setDrawDownStatus(LenderAssociationStatus.DRAWDOWN_FAILED.name());
            lendingApplicationLenderDetailsService.save(lald);
            return;
        }
        log.info("Loan disbursal response success for application id {}", applicationID);
        updateLald(lald);
        updateLendingApplication(lendingApplication);
        updateLad(applicationID);
    }

    private void updateLad(String applicationId) {
        LendingApplicationDetails lendingApplicationDetails = workflowUtil.getLendingApplicationDetails(applicationId);
        lendingApplicationDetails.setStage(LenderAssociationStages.COMPLETED.name());
        lendingApplicationDetailsService.save(lendingApplicationDetails);

    }

    private void updateLendingApplication(LendingApplication lendingApplication) {
        lendingApplication.setSendToNbfc(LendingEnum.SENDTONBFC.YES.name());
        lendingApplication.setNbfcSendDate(new Date());
        lendingApplication.setDisbursalPartner(LendingEnum.DISURSALPARTNERS.BHARATPE.name());
        lendingApplication.setLoanDisbursalStatus(LendingEnum.DISBURSALSTATUS.PENDING.name());
        lendingApplicationServiceV4.save(lendingApplication);
    }

    private void updateLald(LendingApplicationLenderDetails lald) {
        lald.setLeadSubStatus(LeadSubStatus.CALLBACK_PENDING);
        lald.setDrawDownStatus(LenderAssociationStatus.DRAWDOWN_IN_PROGRESS.name());
        lald.setStage(LenderAssociationStages.COMPLETED.name());
        lendingApplicationLenderDetailsService.save(lald);
    }

    private boolean isLoanDisbursalResponseDataSuccess(LenderApiResponse<LoanDisbursalResponse> response) {
        return response.getData().getStatus().equalsIgnoreCase(SUCCESS);
    }

    private LenderBaseRequest<LoanDisbursalRequest> getLoanDisbursalRequest(LendingApplication lendingApplication) {
        LoanDisbursalRequest loanDisbursalRequest = loanDisbursalRequestBuilder.buildRequest(lendingApplication);
        return LenderBaseRequest.<LoanDisbursalRequest>builder()
                .applicationId(String.valueOf(lendingApplication.getId()))
                .customerId(String.valueOf(lendingApplication.getMerchantId()))
                .lender(Lender.valueOf(lendingApplication.getLender()))
                .data(loanDisbursalRequest)
                .build();
    }
}
