package com.bharatpe.lending.lendingplatform.nbfc.service.workflow;

public interface Workflow {
    public void invoke(String applicationId);
    public String getWorkflowName();
}
