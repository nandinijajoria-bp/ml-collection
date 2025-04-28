package com.bharatpe.lending.lendingplatform.lending.util;

import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.lendingplatform.nbfc.enums.LeadStatus;
import com.bharatpe.lending.lendingplatform.nbfc.util.WorkflowUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class StageUtil {
    private final WorkflowUtil workflowUtil;

    public LenderAssociationStatus getLenderAssociationStatus(Long applicationId, String lender) {
        LendingApplicationLenderDetails lald = workflowUtil.getLendingApplicationLenderDetails(String.valueOf(applicationId), lender);
        switch (LeadStatus.valueOf(lald.getLeadStatus())) {
            case CREATE_LEAD:
                return LenderAssociationStatus.LENDER_CHANGE_IN_PROGRESS;
            case BRE: {
                switch (lald.getLeadSubStatus()) {
                    case PENDING:
                    case CALLBACK_PENDING:
                    case REQUEST_CREATION_FAILED:
                        return LenderAssociationStatus.RISK_PENDING;
                    case FAILED:
                        return LenderAssociationStatus.RISK_FAILED;
                    case SUCCESS:
                        return LenderAssociationStatus.LENDER_ASSOCIATION_COMPLETED;
                }
                break;
            }
            case KYC_DOCUMENT:
            case KYC: {
                switch (lald.getLeadSubStatus()) {
                    case PENDING:
                    case CALLBACK_PENDING:
                        return LenderAssociationStatus.KYC_IN_PROGRESS;
                    case FAILED:
                    case REQUEST_CREATION_FAILED:
                        return LenderAssociationStatus.KYC_FAILED;
                    case SUCCESS:
                        return LenderAssociationStatus.KYC_COMPLETED;
                }
                break;
            }
            case EKYC: {
                switch (lald.getLeadSubStatus()) {
                    case PENDING:
                        return LenderAssociationStatus.EKYC_IN_PROGRESS;
                    case FAILED:
                    case REQUEST_CREATION_FAILED:
                        return LenderAssociationStatus.EKYC_FAILED;
                    case SUCCESS:
                        return LenderAssociationStatus.EKYC_COMPLETED;
                }
                break;
            }
            case CKYC: {
                switch (lald.getLeadSubStatus()) {
                    case PENDING:
                        return LenderAssociationStatus.CKYC_IN_PROGRESS;
                    case FAILED:
                    case REQUEST_CREATION_FAILED:
                        return LenderAssociationStatus.CKYC_FAILED;
                    case SUCCESS:
                        return LenderAssociationStatus.CKYC_COMPLETED;
                }
                break;
            }
            case LOAN_DOCUMENT:
                return LenderAssociationStatus.LENDER_ASSOCIATION_COMPLETED;
        }
        log.error("LenderAssociationStatus not found for lead status: {}", lald.getLeadStatus());
        return null;
    }

    public LenderAssociationStages getLenderAssociationStages(Long applicationId, String lender) {
        LendingApplicationLenderDetails lald = workflowUtil.getLendingApplicationLenderDetails(String.valueOf(applicationId), lender);
        switch (LeadStatus.valueOf(lald.getLeadStatus())) {
            case CREATE_LEAD:
                return LenderAssociationStages.INIT;
            case BRE:
                switch (lald.getLeadSubStatus()) {
                    case PENDING:
                    case FAILED:
                    case REQUEST_CREATION_FAILED:
                    case CALLBACK_PENDING:
                        return LenderAssociationStages.BRE;
                    case SUCCESS:
                        return LenderAssociationStages.COMPLETED;
                }
                break;
            case KYC_DOCUMENT:
            case KYC:
                return LenderAssociationStages.KYC;
            case EKYC:
            case CKYC:
                return LenderAssociationStages.EKYC;
            case LOAN_DOCUMENT:
                return LenderAssociationStages.COMPLETED;
        }
        log.error("LenderAssociationStages not found for lead status: {}", lald.getLeadStatus());
        return null;
    }
}
