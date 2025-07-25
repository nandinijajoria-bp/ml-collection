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
import com.bharatpe.lending.lendingplatform.nbfc.registry.WorkflowRegistry;
import com.bharatpe.lending.lendingplatform.nbfc.registry.WorkflowRegistryFactory;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.request.LoanDisbursalRequestBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.database.LendingApplicationDetailsService;
import com.bharatpe.lending.lendingplatform.nbfc.service.database.LendingApplicationLenderDetailsService;
import com.bharatpe.lending.lendingplatform.nbfc.service.database.LendingApplicationServiceV4;
import com.bharatpe.lending.lendingplatform.nbfc.util.WorkflowUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Date;

import static com.bharatpe.lending.lendingplatform.nbfc.constants.DisbursalStatus.SUCCESS;
import static com.bharatpe.lending.lendingplatform.nbfc.constants.WorkflowName.DISBURSAL_WORKFLOW;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.LeadStatus.LOAN_DISBURSAL;

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
    @Lazy
    private final WorkflowRegistryFactory workflowRegistryFactory;


    @Override
    public boolean invoke(String applicationId) {
        LendingApplication lendingApplication = workflowUtil.getLendingApplication(applicationId);
        LendingApplicationLenderDetails lald = workflowUtil.getLendingApplicationLenderDetails(applicationId, lendingApplication.getLender());
        //checking is disbursal already initiated
        if (lald.getStage().equalsIgnoreCase(LenderAssociationStages.COMPLETED.name())
            || (lald.getLeadStatus().equalsIgnoreCase(LOAN_DISBURSAL.name()) &&
                lald.getLeadSubStatus().equals(LeadSubStatus.PENDING))) {
            log.warn("Disbursal already initiated for applicationId: {}", lendingApplication.getId());
            return false;
        }
        lald.setLeadStatus(LOAN_DISBURSAL.name());
        lald.setLeadSubStatus(LeadSubStatus.PENDING);
        lendingApplicationLenderDetailsService.save(lald);
        LenderBaseRequest<LoanDisbursalRequest> loanDisbursalRequest = getLoanDisbursalRequest(lendingApplication);
        if (ObjectUtils.isEmpty(loanDisbursalRequest)) {
            log.warn("Loan disbursal request is empty for application id {}", applicationId);
            lald.setLeadSubStatus(LeadSubStatus.REQUEST_CREATION_FAILED);
            lendingApplicationLenderDetailsService.save(lald);
            return false;
        }
        invokeLoanDisbursal(applicationId, lendingApplication, lald, loanDisbursalRequest);
        return true;
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
        Lender lender = Lender.valueOf(lendingApplication.getLender());
        log.info("Processing loan disbursal response for application id {}: lender={}", applicationID, lender);
        if (ObjectUtils.isEmpty(response) || !response.isSuccess() || !isLoanDisbursalResponseDataSuccess(response)) {
            log.info("Loan disbursal response failure for application id {}", applicationID);
            lald.setLeadSubStatus(LeadSubStatus.FAILED);
            lald.setDrawDownStatus(LenderAssociationStatus.DRAWDOWN_FAILED.name());
            lendingApplicationLenderDetailsService.save(lald);
            return;
        }

        log.info("Loan disbursal response success for application id {}", applicationID);

        WorkflowRegistry workflowRegistry = workflowRegistryFactory
                .getWorkflowRegistry(Lender.valueOf(lendingApplication.getLender()));

        updateLald(lald, workflowRegistry);
        updateLendingApplication(lendingApplication);
        updateLad(applicationID, workflowRegistry);
    }

    private void updateLad(String applicationId, WorkflowRegistry workflowRegistry) {
        LendingApplicationDetails lendingApplicationDetails = workflowUtil.getLendingApplicationDetails(applicationId);
        lendingApplicationDetails.setStage(workflowRegistry.getAssociationStageForWorkflow(this).name());
        lendingApplicationDetailsService.save(lendingApplicationDetails);

    }

    private void updateLendingApplication(LendingApplication lendingApplication) {
        lendingApplication.setSendToNbfc(LendingEnum.SENDTONBFC.YES.name());
        lendingApplication.setNbfcSendDate(new Date());
        lendingApplication.setDisbursalPartner(LendingEnum.DISURSALPARTNERS.BHARATPE.name());
        lendingApplication.setLoanDisbursalStatus(LendingEnum.DISBURSALSTATUS.PENDING.name());
        lendingApplicationServiceV4.save(lendingApplication);
    }

    private void updateLald(LendingApplicationLenderDetails lald, WorkflowRegistry workflowRegistry) {
        lald.setLeadSubStatus(LeadSubStatus.CALLBACK_PENDING);
        lald.setDrawDownStatus(LenderAssociationStatus.DRAWDOWN_IN_PROGRESS.name());
        lald.setStage(workflowRegistry.getAssociationStageForWorkflow(this).name());
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
