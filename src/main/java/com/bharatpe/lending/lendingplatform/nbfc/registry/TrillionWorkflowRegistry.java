package com.bharatpe.lending.lendingplatform.nbfc.registry;

import com.bharatpe.lending.lendingplatform.nbfc.enums.WorkflowStage;
import com.bharatpe.lending.lendingplatform.nbfc.service.workflow.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

import static com.bharatpe.lending.lendingplatform.nbfc.enums.WorkflowStage.*;

@Component
@RequiredArgsConstructor
public class TrillionWorkflowRegistry implements WorkflowRegistry {
    private final CreateLeadWorkflow createLeadWorkflow;
    private final KYCDocumentWorkflow kycDocumentWorkflow;
    private final NachWorkflow nachWorkflow;
    private final BreWorkflow breWorkflow;
    private final KYCWorkflow kycWorkflow;
    private final DisbursalWorkflow disbursalWorkflow;
    private final LoanDocumentWorkflow loanDocumentWorkflow;
    private final Map<WorkflowStage, List<Workflow>> workflowMap = new HashMap<>();

    @PostConstruct
    private void initWorkflowMap() {
        workflowMap.put(CREATE_LEAD, Arrays.asList(createLeadWorkflow));
        workflowMap.put(CREATE_LEAD_AND_KYC_DOCUMENT_UPLOAD, Arrays.asList(createLeadWorkflow, kycDocumentWorkflow));
        workflowMap.put(KYC_DOCUMENT_UPLOAD, Arrays.asList(kycDocumentWorkflow));
        workflowMap.put(LOAN_DOCUMENT_UPLOAD, Arrays.asList(loanDocumentWorkflow, nachWorkflow));
        workflowMap.put(NACH_REGISTRATION, Arrays.asList(nachWorkflow));
        workflowMap.put(BRE, Arrays.asList(breWorkflow));
        workflowMap.put(KYC, Arrays.asList(kycWorkflow));
        workflowMap.put(DISBURSAL, Arrays.asList(disbursalWorkflow));

    }

    @Override
    public List<Workflow> getStageWorkflow(WorkflowStage stage) {
        return workflowMap.getOrDefault(stage, Collections.emptyList());
    }
}
