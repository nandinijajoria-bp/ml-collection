package com.bharatpe.lending.lendingplatform.nbfc.service.workflow;

import com.bharatpe.lending.lendingplatform.nbfc.enums.LeadStatus;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.bharatpe.lending.lendingplatform.nbfc.enums.WorkflowStage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkflowManager {

	public static WorkflowStage getNextWorkflowStage(String lender, String currentStage) {
		try {
			Lender lenderEnum = Lender.valueOf(lender);
			LeadStatus leadStatusEnum = LeadStatus.valueOf(currentStage);

			switch (lenderEnum) {
				case TRILLIONLOANS:
					return getNextStageForCommonLenders(leadStatusEnum);
				case OXYZO:
					return getNextStageForOxyzoLenders(leadStatusEnum);
                case CREDITSAISON:
                    return getNextStageForCsLenders(leadStatusEnum);
                    default:
					log.warn("Invalid lender: {}. Returning an empty workflow stage", lender);
					return null;
			}
		} catch (IllegalArgumentException e) {
			log.warn("Invalid lender or current stage: lender={}, currentStage={}", lender, currentStage, e);
			return null;
		}
	}

	private static WorkflowStage getNextStageForCommonLenders(LeadStatus leadStatus) {
		switch (leadStatus) {
			case LOAN_DOCUMENT:
				return WorkflowStage.NACH_REGISTRATION;
			case KYC_DOCUMENT:
			case KYC:
			case CKYC:
			case EKYC:
				return WorkflowStage.BRE;
			case BRE:
				return WorkflowStage.LOAN_DOCUMENT_UPLOAD;
			default:
				log.warn("No next workflow stage found for invalid lead status: {}", leadStatus);
				return null;
		}
	}

	private static WorkflowStage getNextStageForCsLenders(LeadStatus leadStatus) {
		switch (leadStatus) {
			case KYC_DOCUMENT:
			case KYC:
			case CKYC:
			case EKYC:
				return WorkflowStage.PENNY_DROP;
			case BRE:
				return WorkflowStage.KYC_DOCUMENT_UPLOAD;
			case PENNY_DROP:
				return WorkflowStage.LOAN_DOCUMENT_DOWNLOAD;
			case LOAN_DOCUMENT_DOWNLOAD:
				return WorkflowStage.LOAN_DOCUMENT_UPLOAD; // LoanDocument lead_Status is
			case LOAN_DOCUMENT:
				return WorkflowStage.VKYC;
			default:
				log.error("No next workflow stage found for invalid lead status for cs: {}", leadStatus);
				return null;
		}
	}

	private static WorkflowStage getNextStageForOxyzoLenders(LeadStatus leadStatus) {
		switch (leadStatus) {
			case KYC:
			case KYC_DOCUMENT:
			case CKYC:
			case EKYC:
				return WorkflowStage.BRE;
			case BRE:
				return WorkflowStage.LOAN_DOCUMENT_UPLOAD;
			default:
				log.warn("No next workflow stage found for invalid lead status for cs: {}", leadStatus);
				return null;
		}
	}

	public static WorkflowStage getCurrentWorkflowStage(String lender, String currentStage) {
		try {
			Lender lenderEnum = Lender.valueOf(lender);
			LeadStatus leadStatusEnum = LeadStatus.valueOf(currentStage);

			switch (lenderEnum) {
				case TRILLIONLOANS:
				case OXYZO:
					return getCurrentStageForCommonLenders(leadStatusEnum);
				case CREDITSAISON:
					return getCurrentStageForCommonLendersForCs(leadStatusEnum);
				default:
					log.warn("Invalid lender: {}. Returning an empty workflow stage", lender);
					return null;
			}
		} catch (IllegalArgumentException e) {
			log.warn("Invalid lender or current stage: lender={}, currentStage={}", lender, currentStage, e);
			return null;
		}
	}

	private static WorkflowStage getCurrentStageForCommonLenders(LeadStatus leadStatus) {
		switch (leadStatus) {
			case LOAN_DISBURSAL:
				return WorkflowStage.DISBURSAL;
			case LOAN_DOCUMENT_DOWNLOAD:
				return WorkflowStage.LOAN_DOCUMENT_DOWNLOAD;
			case LOAN_DOCUMENT:
				return WorkflowStage.LOAN_DOCUMENT_UPLOAD;
			case NACH:
				return WorkflowStage.NACH_REGISTRATION;
			default:
				log.warn("No workflow stage found for invalid lead status: {}", leadStatus);
				return null;
		}
	}

	private static WorkflowStage getCurrentStageForCommonLendersForCs(LeadStatus leadStatus) {
		switch (leadStatus) {
			case LOAN_DISBURSAL:
				return WorkflowStage.DISBURSAL;
			case LOAN_DOCUMENT_DOWNLOAD:
				return WorkflowStage.LOAN_DOCUMENT_DOWNLOAD;
			case LOAN_DOCUMENT:
				return WorkflowStage.LOAN_DOCUMENT_DOWNLOAD;
			case VKYC:
				return WorkflowStage.VKYC;
			default:
				log.warn("No workflow stage found for invalid lead status: {}", leadStatus);
				return null;
		}
	}
}