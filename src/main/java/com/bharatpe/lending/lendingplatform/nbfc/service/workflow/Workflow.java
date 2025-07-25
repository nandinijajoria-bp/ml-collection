package com.bharatpe.lending.lendingplatform.nbfc.service.workflow;

public interface Workflow {
    public boolean invoke(String applicationId);
    public String getWorkflowName();
}
