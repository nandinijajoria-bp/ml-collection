package com.bharatpe.lending.lendingplatform.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.bharatpe.lending.lendingplatform.nbfc.enums.WorkflowStage;
import com.bharatpe.lending.lendingplatform.nbfc.registry.WorkflowRegistry;
import com.bharatpe.lending.lendingplatform.nbfc.registry.WorkflowRegistryFactory;
import com.bharatpe.lending.lendingplatform.nbfc.service.workflow.Workflow;
import com.bharatpe.lending.lendingplatform.nbfc.service.workflow.WorkflowManager;
import com.bharatpe.lending.lendingplatform.nbfc.util.WorkflowUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.List;

@Service
@Slf4j
public class ENachRegister {

	@Autowired
	private LendingApplicationLenderDetailsDao laldDao;

	@Autowired
	private WorkflowRegistryFactory workflowRegistryFactory;

	public void pushDetailsToLender(LendingApplication lendingApplication) {

		log.info("Processing nach details to update details to lender for application:{}", lendingApplication.getId());
		if (!"APPROVED".equalsIgnoreCase(lendingApplication.getNachStatus())) {
			log.info("NACH status is:{} skipping NachWorkflow for application:{}",
					lendingApplication.getNachStatus(), lendingApplication.getId());
		} else {
			LendingApplicationLenderDetails lald =
					laldDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(
							lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
			if (!ObjectUtils.isEmpty(lald)) {
				log.info("NACH Approved for application: {}", lendingApplication.getId());
				WorkflowStage nextStage = WorkflowManager.getNextWorkflowStage(lendingApplication.getLender(), lald.getLeadStatus());
				WorkflowRegistry workflowRegistry = workflowRegistryFactory
						.getWorkflowRegistry(Lender.valueOf(lendingApplication.getLender()));
				List<Workflow> workflows = workflowRegistry.getStageWorkflow(nextStage);
				WorkflowUtil.invokeWorkflows(workflows, lendingApplication.getId());
			}
		}
	}
}
