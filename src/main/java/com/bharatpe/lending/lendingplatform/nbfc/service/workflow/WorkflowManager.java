package com.bharatpe.lending.lendingplatform.nbfc.service.workflow;

import com.bharatpe.lending.lendingplatform.nbfc.enums.LeadStatus;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.bharatpe.lending.lendingplatform.nbfc.enums.WorkflowStage;
import lombok.extern.slf4j.Slf4j;

import static com.bharatpe.lending.lendingplatform.nbfc.enums.WorkflowStage.*;
@Slf4j
public class WorkflowManager {

	public static WorkflowStage getNextWorkflowStage(String lender, String currentStage) {

		switch (Lender.valueOf(lender)) {
			case TRILLIONLOANS:
				switch (LeadStatus.valueOf(currentStage)) {
					case LOAN_DOCUMENT:
						return NACH_REGISTRATION;
					case KYC_DOCUMENT:
					case KYC:
					case CKYC:
					case EKYC:
						return BRE;
					case BRE:
						return LOAN_DOCUMENT_UPLOAD;
				}
			default:
				log.error("Invalid lender: {}. Returning an empty workflow stage", lender);
				return null;
		}
	}
	public static WorkflowStage getCurrentWorkflowStage(String lender, String currentStage) {
		switch (Lender.valueOf(lender)) {
			case TRILLIONLOANS:
				switch (LeadStatus.valueOf(currentStage)) {
					case LOAN_DISBURSAL:
						return DISBURSAL;
					case LOAN_DOCUMENT:
						return LOAN_DOCUMENT_UPLOAD;
					case NACH:
						return NACH_REGISTRATION;
					default:
						log.error("No workflow stage found for invalid current stage:{}", currentStage);
						return null;
				}
			default:
				log.error("No workflow stage found for invalid lender:{}", lender);
				return null;
		}
	}
}
