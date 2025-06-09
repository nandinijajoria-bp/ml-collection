package com.bharatpe.lending.lendingplatform.nbfc.registry;

import com.bharatpe.lending.lendingplatform.nbfc.enums.WorkflowStage;
import com.bharatpe.lending.lendingplatform.nbfc.service.workflow.BreWorkflow;
import com.bharatpe.lending.lendingplatform.nbfc.service.workflow.CreateLeadWorkflow;
import com.bharatpe.lending.lendingplatform.nbfc.service.workflow.DisbursalWorkflow;
import com.bharatpe.lending.lendingplatform.nbfc.service.workflow.KYCWorkflow;
import com.bharatpe.lending.lendingplatform.nbfc.service.workflow.LoanDocumentWorkflow;
import com.bharatpe.lending.lendingplatform.nbfc.service.workflow.Workflow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.bharatpe.lending.lendingplatform.nbfc.enums.WorkflowStage.BRE;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.WorkflowStage.CREATE_LEAD;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.WorkflowStage.DISBURSAL;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.WorkflowStage.KYC;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.WorkflowStage.LOAN_DOCUMENT_UPLOAD;

@Component
@RequiredArgsConstructor
public class OxyzoWorkflowRegistry implements WorkflowRegistry {
    private final CreateLeadWorkflow createLeadWorkflow;
    private final BreWorkflow breWorkflow;
    private final KYCWorkflow kycWorkflow;
    private final DisbursalWorkflow disbursalWorkflow;
    private final LoanDocumentWorkflow loanDocumentWorkflow;
    private final Map<WorkflowStage, List<Workflow>> workflowMap = new HashMap<>();

    @PostConstruct
    private void initWorkflowMap() {
        workflowMap.put(CREATE_LEAD, Arrays.asList(createLeadWorkflow));
        workflowMap.put(KYC, Arrays.asList(kycWorkflow, breWorkflow));
        workflowMap.put(LOAN_DOCUMENT_UPLOAD, Arrays.asList(loanDocumentWorkflow));
        workflowMap.put(DISBURSAL, Arrays.asList(disbursalWorkflow));
    }

    @Override
    public List<Workflow> getStageWorkflow(WorkflowStage stage) {
        return workflowMap.getOrDefault(stage, Collections.emptyList());
    }
}

