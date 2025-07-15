package com.bharatpe.lending.lendingplatform.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LeadSubStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.bharatpe.lending.lendingplatform.nbfc.registry.WorkflowRegistryFactory;
import com.bharatpe.lending.lendingplatform.nbfc.service.workflow.Workflow;
import com.bharatpe.lending.lendingplatform.nbfc.util.WorkflowUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.List;

import static com.bharatpe.lending.lendingplatform.nbfc.enums.Lender.OXYZO;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.WorkflowStage.*;

@Slf4j
@Service
public class LoanCreationService {
    @Autowired
    private WorkflowUtil workflowUtil;
    @Autowired
    private WorkflowRegistryFactory workflowRegistryFactory;
    @Autowired
    private LendingApplicationLenderDetailsDao laldDao;

    public void initiateLoanCreationWorkflow(Long applicationId) {
        LendingApplication lendingApplication = workflowUtil.getLendingApplication(applicationId);
        LendingApplicationLenderDetails lald = laldDao
                .findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(
                        applicationId, Status.ACTIVE.name(), lendingApplication.getLender());
        List<Workflow> workflows = null;
        if (ObjectUtils.isEmpty(lald)) { // need to invoke CreateLead workflow only
            workflows = workflowRegistryFactory
                    .getWorkflowRegistry(Lender.valueOf(lendingApplication.getLender())).getStageWorkflow(CREATE_LEAD);
        }
        //Create lead workflow completed invoke KYC Document upload workflow
        if (!ObjectUtils.isEmpty(lald) && lald.getLeadStatus().equals(CREATE_LEAD.name()) &&
                lald.getLeadSubStatus().equals(LeadSubStatus.SUCCESS)) {
            if (lendingApplication.getLender().equals(OXYZO.name())) {
                workflows = workflowRegistryFactory
                        .getWorkflowRegistry(Lender.valueOf(lendingApplication.getLender())).getStageWorkflow(KYC);
                log.info("Invoking KYC workflow for OXYZO lender, applicationId={}", applicationId);
            } else {
                workflows = workflowRegistryFactory
                        .getWorkflowRegistry(Lender.valueOf(lendingApplication.getLender())).getStageWorkflow(KYC_DOCUMENT_UPLOAD);
                log.info("Invoking KYC_DOCUMENT_UPLOAD workflow for applicationId={}", applicationId);

            }
        }
        WorkflowUtil.invokeWorkflows(workflows, applicationId);
    }

}
