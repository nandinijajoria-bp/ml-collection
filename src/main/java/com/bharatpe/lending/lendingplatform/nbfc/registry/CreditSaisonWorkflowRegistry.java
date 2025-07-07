package com.bharatpe.lending.lendingplatform.nbfc.registry;

import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.lendingplatform.nbfc.enums.WorkflowStage;
import com.bharatpe.lending.lendingplatform.nbfc.service.workflow.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

import static com.bharatpe.lending.lendingplatform.nbfc.enums.WorkflowStage.*;

@Component
@RequiredArgsConstructor
public class CreditSaisonWorkflowRegistry implements WorkflowRegistry {
    private final CreateLeadWorkflow createLeadWorkflow;
    private final KYCDocumentWorkflow kycDocumentWorkflow;
    private final KYCWorkflow kycWorkflow;
    private final BreWorkflow breWorkflow;
    private final PennyDropWorkflow pennyDropWorkflow;
    private final DisbursalWorkflow disbursalWorkflow;
    private final LoanDocumentDownloadWorkflow loanDocumentDownloadWorkflow;
    private final LoanDocumentWorkflow loanDocumentWorkflow;
    private final Map<WorkflowStage, List<Workflow>> workflowMap = new HashMap<>();
    private final Map<Workflow, LenderAssociationStages> workflowStage = new HashMap<>();

    @PostConstruct
    private void initWorkflowMap() {
        workflowMap.put(CREATE_LEAD, Arrays.asList(createLeadWorkflow));
        workflowMap.put(KYC_DOCUMENT_UPLOAD, Arrays.asList(kycDocumentWorkflow));
        workflowMap.put(LOAN_DOCUMENT_DOWNLOAD, Arrays.asList(loanDocumentDownloadWorkflow, loanDocumentWorkflow));
        workflowMap.put(LOAN_DOCUMENT_UPLOAD, Arrays.asList(loanDocumentWorkflow));
        workflowMap.put(BRE, Arrays.asList(breWorkflow));
        workflowMap.put(KYC, Arrays.asList(kycWorkflow));
        workflowMap.put(DISBURSAL, Arrays.asList(disbursalWorkflow));
        workflowMap.put(PENNY_DROP, Arrays.asList(pennyDropWorkflow));
    }

    @PostConstruct
    private void initWorkflowStageMap() {
        workflowStage.put(createLeadWorkflow, LenderAssociationStages.KYC);
        workflowStage.put(kycDocumentWorkflow, LenderAssociationStages.BRE);
        workflowStage.put(breWorkflow, LenderAssociationStages.PENNY_DROP);
        workflowStage.put(pennyDropWorkflow, LenderAssociationStages.ASSC_COMPLETED);
        workflowStage.put(loanDocumentDownloadWorkflow, LenderAssociationStages.ASSC_COMPLETED);
        workflowStage.put(loanDocumentWorkflow, LenderAssociationStages.DRAWDOWN);
        workflowStage.put(disbursalWorkflow, LenderAssociationStages.COMPLETED);
    }

    @Override
    public List<Workflow> getStageWorkflow(WorkflowStage stage) {
        return workflowMap.getOrDefault(stage, Collections.emptyList());
    }

    @Override
    public LenderAssociationStages getAssociationStageForWorkflow(Workflow workflow) {
        return workflowStage.get(workflow);
    }
}
