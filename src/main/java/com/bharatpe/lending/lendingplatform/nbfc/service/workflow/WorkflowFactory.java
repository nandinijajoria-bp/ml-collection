package com.bharatpe.lending.lendingplatform.nbfc.service.workflow;

import com.bharatpe.lending.lendingplatform.nbfc.enums.WorkflowStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class WorkflowFactory {

	private final BreWorkflow breWorkflow;
	private final CreateLeadWorkflow createLeadWorkflow;
	private final KYCDocumentWorkflow kycDocumentWorkflow;
	private final KYCWorkflow kycWorkflow;
	private final LoanDocumentDigiSignWorkflow loanDocumentDigiSignWorkflow;
	private final LoanDocumentWorkflow loanDocumentWorkflow;
	private final LoanDocumentDownloadWorkflow loanDocumentDownloadWorkflow;
	private final NachWorkflow nachWorkflow;
	private final LoanSanctionWorkflow sanctionWorkflow;
	private final UpdateLeadWorkflow updateLeadWorkflow;

	public Workflow getWorkflow(WorkflowStage stage) {
		switch (stage) {
			case BRE:
				return breWorkflow;
			case CREATE_LEAD:
				return createLeadWorkflow;
			case KYC_DOCUMENT_UPLOAD:
				return kycDocumentWorkflow;
			case KYC:
				return kycWorkflow;
			case DIGI_SIGN:
				return loanDocumentDigiSignWorkflow;
			case LOAN_DOCUMENT_DOWNLOAD:
				return loanDocumentDownloadWorkflow;
			case LOAN_DOCUMENT_UPLOAD:
				return loanDocumentWorkflow;
			case NACH_REGISTRATION:
				return nachWorkflow;
			case SANCTION:
				return sanctionWorkflow;
			case UPDATE_LEAD:
				return updateLeadWorkflow;
			default:
				log.warn("Invalid workflow stage: {}. Returning null", stage);
				return null;
		}
	}

}
