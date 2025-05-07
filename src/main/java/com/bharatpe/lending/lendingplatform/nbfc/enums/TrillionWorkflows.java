package com.bharatpe.lending.lendingplatform.nbfc.enums;

public enum TrillionWorkflows {

    CREATE_LEAD("trillion_create_lead"),
    KYC_DOCUMENT_UPLOAD("trillion_kyc_document_upload"),
    KYC("trillion_kyc"),
    BRE("trillion_bre"),
    UPDATE_LEAD("trillion_update_lead"),
    DIGI_SIGN("trillion_digi_sign"),
    LOAN_DOCUMENT_UPLOAD("trillion_loan_document_upload"),
    NACH_REGISTRATION("trillion_nach_registration"),
    SANCTION("trillion_sanction"),
    DISBURSAL("trillion_disbursal");

    private final String workflowName;

    TrillionWorkflows(String workflowName) {
        this.workflowName = workflowName;
    }

    public String getWorkflowName() {
        return workflowName;
    }
}
