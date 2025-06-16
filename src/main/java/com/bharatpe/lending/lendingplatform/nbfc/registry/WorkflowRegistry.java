package com.bharatpe.lending.lendingplatform.nbfc.registry;

import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.lendingplatform.nbfc.enums.WorkflowStage;
import com.bharatpe.lending.lendingplatform.nbfc.service.workflow.Workflow;

import java.util.List;

public interface WorkflowRegistry {
    public List<Workflow> getStageWorkflow(WorkflowStage stage);

    LenderAssociationStages getAssociationStageForWorkflow(Workflow workflow);
}
